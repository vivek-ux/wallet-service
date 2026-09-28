import os, json, hashlib, time, random, platform
from pathlib import Path
import numpy as np, pandas as pd, torch
from PIL import Image
import torchvision.transforms as T
from datasets import load_dataset
import torch.nn.functional as F

ROOT=Path(__file__).parent/'outputs'/'local_pilot'; ROOT.mkdir(parents=True,exist_ok=True)
SEED=20260927; random.seed(SEED); np.random.seed(SEED); torch.manual_seed(SEED)
DEVICE=torch.device('mps' if torch.backends.mps.is_available() else 'cpu')
MODEL_PATH=Path('/private/tmp/sscd_disc_large.torchscript.pt')
assert MODEL_PATH.exists(), MODEL_PATH
print('device',DEVICE,flush=True)
model=torch.jit.load(str(MODEL_PATH),map_location='cpu').eval().to(DEVICE)
for p in model.parameters(): p.requires_grad_(False)
pre=T.Compose([T.Resize(256),T.CenterCrop(224),T.ToTensor(),T.Normalize([0.485,0.456,0.406],[0.229,0.224,0.225])])

def embed(images,batch=8):
    out=[]
    with torch.no_grad():
      for i in range(0,len(images),batch):
        x=torch.stack([pre(im) for im in images[i:i+batch]]).to(DEVICE)
        z=model(x)
        if isinstance(z,(tuple,list)): z=z[0]
        out.append(F.normalize(z.float(),dim=1).cpu())
    return torch.cat(out)

def crops(im,g=3):
    w,h=im.size; return [im.crop((round(x*w/g),round(y*h/g),round((x+1)*w/g),round((y+1)*h/g))) for y in range(g) for x in range(g)]
def local_score(q,c):
    a=embed(crops(q)); b=embed(crops(c)); s=a@b.T
    return float(s.flatten().topk(min(3,s.numel())).values.mean())

# Stream only enough genuine MagicBrush turns for a small pilot.
ds=load_dataset('osunlp/MagicBrush',split='train',streaming=True)
records=[]; seen={}
for row in ds:
    sid=str(row['img_id']); turn=int(row['turn_index'])
    # Keep up to 2 turns per session and stop at 12 sessions.
    if sid not in seen: seen[sid]=0
    if seen[sid]>=2: continue
    records.append({'session_id':sid,'turn':turn,'source':row['source_img'].convert('RGB'),'target':row['target_img'].convert('RGB'),'instruction':str(row['instruction'])})
    seen[sid]+=1
    if len(seen)>=12 and len(records)>=18: break
if len(records)<6: raise RuntimeError(f'Only {len(records)} rows loaded')
# session split, all turns together
sessions=sorted(seen); random.Random(SEED).shuffle(sessions)
cut=max(2,int(.7*len(sessions))); train_s=set(sessions[:cut]); test_s=set(sessions[cut:])
train=[r for r in records if r['session_id'] in train_s]; test=[r for r in records if r['session_id'] in test_s]
# gallery is one original per session; all other originals are distractors
orig={r['session_id']:r['source'] for r in records}
gallery=[{'gallery_id':sid,'image':im} for sid,im in orig.items()]
print('sessions',len(sessions),'train turns',len(train),'test turns',len(test),'gallery',len(gallery),flush=True)
pd.DataFrame([{k:v for k,v in r.items() if k not in ['source','target']} for r in records]).to_csv(ROOT/'dataset_manifest.csv',index=False)
json.dump({'seed':SEED,'train_sessions':sorted(train_s),'test_sessions':sorted(test_s),'model':'SSCD sscd_disc_large TorchScript official checkpoint','device':str(DEVICE)},open(ROOT/'run_record.json','w'),indent=2)

def global_rank(recs):
 q=embed([r['target'] for r in recs]); g=embed([x['image'] for x in gallery]); sim=q@g.T; return q,g,sim

def rank_from_scores(recs,sim,method):
 out=[]
 for i,r in enumerate(recs):
  order=torch.argsort(sim[i],descending=True).tolist(); target=next(j for j,x in enumerate(gallery) if x['gallery_id']==r['session_id'])
  out.append({'query_id':f"{r['session_id']}_turn{r['turn']}",'session_id':r['session_id'],'turn':r['turn'],'method':method,'target_rank':order.index(target)+1,'ranking':[gallery[j]['gallery_id'] for j in order]})
 return out

def metrics(rs):
 return {'queries':len(rs),'gallery_size':len(gallery),**{f'Recall@{k}':round(float(np.mean([r['target_rank']<=k for r in rs])),4) for k in [1,5,10]}}
# Test global baseline
tq,tg,tsim=global_rank(test); global_r=rank_from_scores(test,tsim,'sscd_global'); print('global',metrics(global_r),flush=True)
# Fixed local reranking
def fixed_rank(recs,sim,alpha=.75):
 out=[]
 for i,r in enumerate(recs):
  top=torch.argsort(sim[i],descending=True).tolist(); ls=np.array([local_score(r['target'],gallery[j]['image']) for j in top],np.float32); ln=(ls-ls.min())/(ls.max()-ls.min()+1e-8) if len(ls)>1 else ls
  score={j:alpha*float(sim[i,j])+(1-alpha)*float(v) for j,v in zip(top,ln)}; order=sorted(top,key=lambda j:score[j],reverse=True); target=next(j for j,x in enumerate(gallery) if x['gallery_id']==r['session_id'])
  out.append({'query_id':f"{r['session_id']}_turn{r['turn']}",'session_id':r['session_id'],'turn':r['turn'],'method':'sscd_plus_fixed_patch','target_rank':order.index(target)+1,'ranking':[gallery[j]['gallery_id'] for j in order]})
 return out
fixed_r=fixed_rank(test,tsim); print('fixed',metrics(fixed_r),flush=True)
# Fusion features. local computation is reused for training/test in this small pilot.
def pair_feats(recs):
 _,_,sim=global_rank(recs); X=[]; y=[]
 for i,r in enumerate(recs):
  order=torch.argsort(sim[i],descending=True).tolist(); ls=np.array([local_score(r['target'],gallery[j]['image']) for j in order],np.float32); ln=(ls-ls.min())/(ls.max()-ls.min()+1e-8) if len(ls)>1 else ls; pos=next(j for j,x in enumerate(gallery) if x['gallery_id']==r['session_id'])
  for j,v in zip(order,ln): X.append([float(sim[i,j]),float(v)]); y.append(float(j==pos))
 return torch.tensor(X),torch.tensor(y),sim
X,y,_=pair_feats(train); net=torch.nn.Sequential(torch.nn.Linear(2,16),torch.nn.ReLU(),torch.nn.Linear(16,1)); opt=torch.optim.Adam(net.parameters(),lr=1e-3); pos_weight=torch.tensor([(y==0).sum()/max(1,(y==1).sum())]); lossfn=torch.nn.BCEWithLogitsLoss(pos_weight=pos_weight)
for ep in range(40):
 opt.zero_grad(); loss=lossfn(net(X).squeeze(1),y); loss.backward(); opt.step()
# fusion test
def fusion_rank(recs):
 _,_,sim=global_rank(recs); out=[]
 for i,r in enumerate(recs):
  order=torch.argsort(sim[i],descending=True).tolist(); ls=np.array([local_score(r['target'],gallery[j]['image']) for j in order],np.float32); ln=(ls-ls.min())/(ls.max()-ls.min()+1e-8) if len(ls)>1 else ls; x=torch.tensor([[float(sim[i,j]),float(v)] for j,v in zip(order,ln)]); fs=net(x).detach().squeeze(1).numpy(); order=[j for _,j in sorted(zip(fs,order),reverse=True)]; target=next(j for j,g in enumerate(gallery) if g['gallery_id']==r['session_id']); out.append({'query_id':f"{r['session_id']}_turn{r['turn']}",'session_id':r['session_id'],'turn':r['turn'],'method':'fusion_mlp','target_rank':order.index(target)+1,'ranking':[gallery[j]['gallery_id'] for j in order]})
 return out
fusion_r=fusion_rank(test); print('fusion',metrics(fusion_r),flush=True)
allr=global_r+fixed_r+fusion_r; pd.DataFrame(allr).to_csv(ROOT/'per_query_rankings.csv',index=False); pd.DataFrame([{'method':'sscd_global',**metrics(global_r)},{'method':'sscd_plus_fixed_patch',**metrics(fixed_r)},{'method':'fusion_mlp',**metrics(fusion_r)}]).to_csv(ROOT/'comparison_table.csv',index=False); torch.save(net.state_dict(),ROOT/'fusion_mlp.pt')
print('saved',ROOT,flush=True)
