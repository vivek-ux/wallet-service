import argparse, hashlib, json, random, time
from pathlib import Path
import numpy as np, pandas as pd, torch
from PIL import Image
import torchvision.transforms as T
from datasets import load_dataset
import torch.nn.functional as F

ap=argparse.ArgumentParser(); ap.add_argument('--max-sessions',type=int,default=100); ap.add_argument('--max-turns',type=int,default=3); ap.add_argument('--seed',type=int,default=20260927); args=ap.parse_args()
ROOT=Path(__file__).parent/'outputs'/f'pilot_{args.max_sessions}_sessions'; ROOT.mkdir(parents=True,exist_ok=True); CACHE=ROOT/'cache'; CACHE.mkdir(exist_ok=True)
random.seed(args.seed); np.random.seed(args.seed); torch.manual_seed(args.seed)
DEVICE=torch.device('mps' if torch.backends.mps.is_available() else 'cpu'); print('device',DEVICE,flush=True)
model=torch.jit.load(str(Path(__file__).parent/'outputs'/'pilot_100_sessions'/'sscd_disc_mixup.torchscript.pt'),map_location='cpu').eval().to(DEVICE)
pre=T.Compose([T.Resize(256),T.CenterCrop(224),T.ToTensor(),T.Normalize([.485,.456,.406],[.229,.224,.225])])
def key(im): return hashlib.sha256(im.tobytes()).hexdigest()[:24]
def crop_list(im,g=3):
 w,h=im.size; return [im.crop((round(x*w/g),round(y*h/g),round((x+1)*w/g),round((y+1)*h/g))) for y in range(g) for x in range(g)]
@torch.no_grad()
def encode(images,batch=4):
 out=[]
 for i in range(0,len(images),batch):
  z=model(torch.stack([pre(x) for x in images[i:i+batch]]).to(DEVICE)); z=z[0] if isinstance(z,(list,tuple)) else z; out.append(F.normalize(z.float(),dim=1).cpu())
 return torch.cat(out)
def cached_embeddings(images,tag):
 out=[]; miss=[]; paths=[]
 for im in images:
  p=CACHE/f'{tag}_{key(im)}.pt'
  if p.exists(): out.append(torch.load(p,map_location='cpu',weights_only=True))
  else: out.append(None); miss.append(im); paths.append(p)
 if miss:
  print('encoding',len(miss),tag,flush=True); z=encode(miss)
  for i,p in enumerate(paths): torch.save(z[i],p)
  j=0
  for i,v in enumerate(out):
   if v is None: out[i]=z[j]; j+=1
 return torch.stack(out)
# Load bounded genuine MagicBrush sample.
ds=load_dataset('osunlp/MagicBrush',split='train',streaming=True)
rows=[]; counts={}
for row in ds:
 sid=str(row['img_id']); counts.setdefault(sid,0)
 if counts[sid]>=args.max_turns: continue
 rows.append({'session_id':sid,'turn':int(row['turn_index']),'source':row['source_img'].convert('RGB'),'target':row['target_img'].convert('RGB'),'instruction':str(row['instruction'])}); counts[sid]+=1
 if len(counts)>=args.max_sessions and all(v>=args.max_turns for v in counts.values()): break
 if len(counts)>=args.max_sessions and len(rows)>=args.max_sessions*2: break
sessions=list(counts); random.Random(args.seed).shuffle(sessions); ntest=max(1,round(.2*len(sessions))); test_s=set(sessions[:ntest]); train_s=set(sessions[ntest:]); train=[r for r in rows if r['session_id'] in train_s]; test=[r for r in rows if r['session_id'] in test_s]
orig={r['session_id']:r['source'] for r in rows}; gallery=[{'gallery_id':sid,'image':im} for sid,im in orig.items()]
print('sessions',len(sessions),'train turns',len(train),'test turns',len(test),'gallery',len(gallery),flush=True)
manifest=pd.DataFrame([{k:v for k,v in r.items() if k not in ('source','target')} for r in rows]); manifest.to_csv(ROOT/'dataset_manifest.csv',index=False)
# Cache all unique global and 3x3 patch embeddings exactly once.
all_imgs={key(r['target']):r['target'] for r in rows}; all_imgs.update({key(r['source']):r['source'] for r in rows}); ims=list(all_imgs.values()); gz=cached_embeddings([g['image'] for g in gallery],'global'); qz=cached_embeddings([r['target'] for r in rows],'global');
patches={}
for im in ims: patches[key(im)]=cached_embeddings(crop_list(im),'patch')
def local(q,c):
 s=patches[key(q)]@patches[key(c)].T; return float(s.flatten().topk(3).values.mean())
# maps by record index
qmap={id(r):qz[i] for i,r in enumerate(rows)}; gmat=torch.stack([cached_embeddings([g['image']],'global')[0] for g in gallery])
def global_matrix(recs): return torch.stack([qmap[id(r)] for r in recs])@gmat.T
def make_rank(recs,kind):
 sm=global_matrix(recs); out=[]
 for i,r in enumerate(recs):
  order=torch.argsort(sm[i],descending=True).tolist(); localvals=np.array([local(r['target'],gallery[j]['image']) for j in order],np.float32); norm=(localvals-localvals.min())/(localvals.max()-localvals.min()+1e-8) if len(localvals)>1 else localvals; target=next(j for j,g in enumerate(gallery) if g['gallery_id']==r['session_id'])
  if kind=='global': final=order
  else:
   if kind=='fixed': scores={j:.75*float(sm[i,j])+.25*float(v) for j,v in zip(order,norm)}
   else:
    x=torch.tensor([[float(sm[i,j]),float(v)] for j,v in zip(order,norm)]); scores={j:float(net(x).detach()[k]) for k,j in enumerate(order)}
   final=sorted(order,key=lambda j:scores[j],reverse=True)
  out.append({'query_id':f"{r['session_id']}_turn{r['turn']}",'session_id':r['session_id'],'turn':r['turn'],'method':kind,'target_rank':final.index(target)+1,'ranking':[gallery[j]['gallery_id'] for j in final]})
 return out
def metrics(rs): return {'queries':len(rs),'gallery_size':len(gallery),**{f'Recall@{k}':round(float(np.mean([r['target_rank']<=k for r in rs])),4) for k in (1,5,10)}}
# Train fusion on train sessions; negatives are all other gallery sources.
sm=global_matrix(train); X=[]; y=[]
for i,r in enumerate(train):
 order=torch.argsort(sm[i],descending=True).tolist(); vals=np.array([local(r['target'],gallery[j]['image']) for j in order],np.float32); vals=(vals-vals.min())/(vals.max()-vals.min()+1e-8); pos=next(j for j,g in enumerate(gallery) if g['gallery_id']==r['session_id'])
 for j,v in zip(order,vals): X.append([float(sm[i,j]),float(v)]); y.append(float(j==pos))
X=torch.tensor(X); y=torch.tensor(y); net=torch.nn.Sequential(torch.nn.Linear(2,16),torch.nn.ReLU(),torch.nn.Linear(16,1)); opt=torch.optim.Adam(net.parameters(),lr=1e-3); loss=torch.nn.BCEWithLogitsLoss(pos_weight=torch.tensor([(y==0).sum()/max(1,(y==1).sum())]))
for _ in range(40): opt.zero_grad(); l=loss(net(X).squeeze(1),y); l.backward(); opt.step()
test_global=make_rank(test,'global'); test_fixed=make_rank(test,'fixed'); test_fusion=make_rank(test,'fusion')
comparison=pd.DataFrame([{'method':'sscd_global',**metrics(test_global)},{'method':'sscd_plus_fixed_patch',**metrics(test_fixed)},{'method':'fusion_mlp',**metrics(test_fusion)}]); comparison.to_csv(ROOT/'comparison_table.csv',index=False); pd.DataFrame(test_global+test_fixed+test_fusion).to_csv(ROOT/'per_query_rankings.csv',index=False); torch.save(net.state_dict(),ROOT/'fusion_mlp.pt'); json.dump({'seed':args.seed,'sessions':len(sessions),'train_sessions':sorted(train_s),'test_sessions':sorted(test_s),'device':str(DEVICE),'checkpoint':'sscd_disc_mixup.torchscript.pt'},open(ROOT/'run_record.json','w'),indent=2); print(comparison.to_string(index=False),flush=True); print('saved',ROOT,flush=True)
