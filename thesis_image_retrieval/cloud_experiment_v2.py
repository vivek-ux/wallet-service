"""Bounded MagicBrush first-original retrieval pilot. No novelty claim."""
from pathlib import Path
import argparse, hashlib, json, random, time, sys, subprocess, shutil, io
from concurrent.futures import ThreadPoolExecutor
from threading import local
import numpy as np
import pandas as pd
import torch
import torch.nn.functional as F
import torchvision.transforms as T
import requests
from PIL import Image

p=argparse.ArgumentParser(); p.add_argument('--root',default='/content/drive/MyDrive/thesis_image_retrieval/cloud_v2'); p.add_argument('--rows',type=int,default=300); p.add_argument('--config',default='default'); p.add_argument('--split',default='train'); a=p.parse_args()
root=Path(a.root); root.mkdir(parents=True,exist_ok=True)
cache=root/'features'; cache.mkdir(exist_ok=True)
seed=20260927; random.seed(seed); np.random.seed(seed); torch.manual_seed(seed)
device='cuda' if torch.cuda.is_available() else 'cpu'; torch.set_num_threads(2)
print('DEVICE',device,flush=True)
thread_state=local()
def http_session():
 if not hasattr(thread_state,'session'):
  thread_state.session=requests.Session(); thread_state.session.headers.update({'User-Agent':'MTech-Image-Retrieval-Pilot/1.0'})
 return thread_state.session
def get_with_retry(url, *, params=None, stream=False, timeout=(15,60), attempts=5):
 last=None
 for attempt in range(attempts):
  try:
   response=http_session().get(url,params=params,stream=stream,timeout=timeout)
   response.raise_for_status(); return response
  except Exception as exc:
   last=exc; wait=min(16,2**attempt)
   print(f'NETWORK RETRY {attempt+1}/{attempts}: {type(exc).__name__}; waiting {wait}s',flush=True)
   if attempt+1<attempts: time.sleep(wait)
 raise RuntimeError(f'Network request failed after {attempts} attempts: {url}') from last
url='https://dl.fbaipublicfiles.com/sscd-copy-detection/sscd_disc_mixup.torchscript.pt'
ckpt=root/'sscd_disc_mixup.torchscript.pt'
run_record={'status':'running','stage':'loading_checkpoint','seed':seed,'device':device,'dataset':'osunlp/MagicBrush','requested_rows':a.rows,'output_root':str(root)}
def save_run_record(): (root/'run_record.json').write_text(json.dumps(run_record,indent=2))
save_run_record()
if ckpt.exists() and ckpt.stat().st_size<1_000_000: ckpt.unlink()
if not ckpt.exists():
 part=ckpt.with_suffix(ckpt.suffix+'.part')
 for attempt in range(6):
  try:
   with get_with_retry(url,stream=True,timeout=(20,120),attempts=5) as response, part.open('wb') as f:
    for chunk in response.iter_content(1024*1024):
     if chunk: f.write(chunk)
   part.replace(ckpt); break
  except Exception as exc:
   part.unlink(missing_ok=True)
   if attempt==5: raise
   wait=min(45,2**attempt); print(f'CHECKPOINT RETRY {attempt+1}/6: {type(exc).__name__}; waiting {wait}s',flush=True); time.sleep(wait)
ckhash=hashlib.sha256(ckpt.read_bytes()).hexdigest()
model=torch.jit.load(str(ckpt),map_location='cpu').eval().to(device)
for v in model.parameters(): v.requires_grad_(False)
pre=T.Compose([T.Resize((288,288)),T.ToTensor(),T.Normalize([.485,.456,.406],[.229,.224,.225])])
def key(im): return hashlib.sha256(str(im.size).encode()+im.tobytes()).hexdigest()
dataset='osunlp/MagicBrush'
api='https://datasets-server.huggingface.co'
run_record.update(stage='loading_dataset'); save_run_record()
split_response=get_with_retry(f'{api}/splits',params={'dataset':dataset}).json()
splits=split_response['splits']
available={(x['config'],x['split']) for x in splits}
if (a.config,a.split) not in available:
 raise ValueError(f'Unknown {a.config}/{a.split}; available splits: {sorted(available)}')
raw_rows=[]
dataset_rows_total=None
target_rows=a.rows+1  # one look-ahead row marks whether the bounded sample ends mid-session
offset=0
while offset<target_rows and (dataset_rows_total is None or offset<dataset_rows_total):
 length=min(100,target_rows-offset,dataset_rows_total-offset if dataset_rows_total is not None else 100)
 page=get_with_retry(f'{api}/rows',params={'dataset':dataset,'config':a.config,'split':a.split,'offset':offset,'length':length}).json()
 dataset_rows_total=page.get('num_rows_total',dataset_rows_total)
 raw_rows.extend(page['rows'])
 print(f'DATASET ROW METADATA {len(raw_rows)}/{a.rows}',flush=True)
 if not page['rows']: break
 offset+=len(page['rows'])
expected_raw_rows=min(target_rows,dataset_rows_total) if dataset_rows_total is not None else target_rows
if len(raw_rows)<expected_raw_rows: raise RuntimeError(f'Incomplete Dataset Viewer slice: received {len(raw_rows)} of {expected_raw_rows} requested rows.')
sample_is_complete=(len(raw_rows)>=dataset_rows_total) if dataset_rows_total is not None and dataset_rows_total<=a.rows else len(raw_rows)>a.rows and raw_rows[a.rows]['row']['img_id']!=raw_rows[a.rows-1]['row']['img_id']
raw_rows=raw_rows[:a.rows]
if len(raw_rows)==a.rows and not sample_is_complete:
 cut_id=raw_rows[-1]['row']['img_id']; raw_rows=[item for item in raw_rows if item['row']['img_id']!=cut_id]
 print('DROPPED SESSION CUT BY SAMPLE LIMIT',str(cut_id),flush=True)
ordered_ids=[str(item['row']['img_id']) for item in raw_rows]
seen_ids=set(); previous_id=None
for sid in ordered_ids:
 if sid!=previous_id and sid in seen_ids: raise RuntimeError('MagicBrush rows are not grouped by session; refusing the look-ahead completeness assumption.')
 seen_ids.add(sid); previous_id=sid
image_urls=sorted({cell['src'] for item in raw_rows for name in ('source_img','target_img')
                   for cell in [item['row'][name]] if isinstance(cell,dict) and cell.get('src')})
if len(image_urls)==0: raise RuntimeError('Dataset Viewer returned no downloadable image URLs.')
def download_image(src): return src,Image.open(io.BytesIO(get_with_retry(src).content)).convert('RGB')
image_cache={}
with ThreadPoolExecutor(max_workers=6) as pool:
 for i,(src,image) in enumerate(pool.map(download_image,image_urls),start=1):
  image_cache[src]=image
  if i%50==0 or i==len(image_urls): print(f'DOWNLOADED IMAGES {i}/{len(image_urls)}',flush=True)
rows=[]
for wrapped in raw_rows:
 r=wrapped['row']; source=r['source_img']; target=r['target_img']
 if not isinstance(source,dict) or not isinstance(target,dict) or source.get('src') not in image_cache or target.get('src') not in image_cache:
  raise ValueError('Dataset Viewer image field is missing or could not be downloaded.')
 rows.append(dict(dataset_row_idx=int(wrapped['row_idx']),sid=str(r['img_id']),turn=int(r['turn_index']),source=image_cache[source['src']].copy(),target=image_cache[target['src']].copy(),instruction=str(r['instruction'])))
print(f'DATASET IMAGES READY {len(rows)}/{a.rows}',flush=True)
if not rows: raise RuntimeError('Dataset Viewer returned no rows; no experiment results were produced.')
# Recover FIRST original, never overwrite it with a later edit.
groups={}
for r in rows: groups.setdefault(r['sid'],[]).append(r)
first_turn=min(r['turn'] for r in rows)
groups={s:sorted(rs,key=lambda x:x['turn']) for s,rs in groups.items() if min(r['turn'] for r in rs)==first_turn}
if not groups: raise RuntimeError('No complete sessions beginning with the first edit were available in this sample.')
for sid,rs in groups.items():
 turns=[r['turn'] for r in rs]
 if turns!=list(range(first_turn,first_turn+len(turns))): raise RuntimeError(f'Non-consecutive edit stages in session {sid}: {turns}')
originals={s:rs[0]['source'] for s,rs in groups.items()}
# Duplicate originals share a split even when session IDs differ.
hashes={s:key(im) for s,im in originals.items()}
u=sorted(set(hashes.values())); random.Random(seed).shuffle(u)
assert len(u)>=15, 'Too few independent originals'
nt=max(3,round(.2*len(u))); nv=max(2,round(.15*len(u)))
assignment={h:('test' if i<nt else 'val' if i<nt+nv else 'train') for i,h in enumerate(u)}
splits={s:assignment[hashes[s]] for s in groups}
# Stop rather than silently retain identical images across splits.
seen={}
for s,rs in groups.items():
 for im in [originals[s]]+[r['target'] for r in rs]:
  h=key(im)
  assert h not in seen or seen[h]==splits[s], 'Identical image crosses splits'
  seen[h]=splits[s]
manifest=[]
for s,rs in groups.items():
 for r in rs: manifest.append(dict(dataset_row_idx=r['dataset_row_idx'],session_id=s,turn=r['turn'],split=splits[s],original_sha256=hashes[s],edited_sha256=key(r['target']),instruction=r['instruction']))
pd.DataFrame(manifest).to_csv(root/'dataset_manifest.csv',index=False)
split_counts=pd.DataFrame(manifest).groupby('split').size().to_dict()
print('LEAKAGE CHECK PASSED',split_counts,flush=True)
run_record.update(stage='extracting_features',dataset_viewer_api=api,config=a.config,split=a.split,dataset_rows_total=dataset_rows_total,bounded_rows=len(rows),split_counts=split_counts)
save_run_record()
# Cache one full image + nine patches per image; no recomputation during fusion training.
@torch.inference_mode()
def features(im):
 path=cache/(key(im)+'_'+ckhash[:12]+'_288_grid3.pt')
 if path.exists(): return torch.load(path,map_location='cpu',weights_only=True)
 w,h=im.size; crops=[im]+[im.crop((round(x*w/3),round(y*h/3),round((x+1)*w/3),round((y+1)*h/3))) for y in range(3) for x in range(3)]
 out=[]
 for b in range(0,len(crops),10 if device=='cuda' else 2):
  z=model(torch.stack([pre(x) for x in crops[b:b+(10 if device=='cuda' else 2)]]).to(device)); out.append(F.normalize(z.float(),dim=-1).cpu())
 z=torch.cat(out); torch.save(z,path); return z
start=time.perf_counter(); all_images={}
for s,rs in groups.items():
 all_images[key(originals[s])]=originals[s]
 for r in rs: all_images[key(r['target'])]=r['target']
feat={}
for i,(h,im) in enumerate(all_images.items()):
 feat[h]=features(im)
 if i%25==0: print('FEATURES',i,'/',len(all_images),flush=True)
feature_seconds=time.perf_counter()-start
# Gallery for each split consists solely of its original images. Other originals are distractors.
def build(split,final_only=False):
 gids=sorted({hashes[s] for s in groups if splits[s]==split})
 g=torch.stack([feat[h][0] for h in gids]); gp=torch.stack([feat[h][1:] for h in gids]); out=[]
 for s,rs in groups.items():
  if splits[s]!=split: continue
  for r in ([rs[-1]] if final_only else rs):
   q=feat[key(r['target'])]; start=time.perf_counter(); glob=q[0]@g.T; order=torch.argsort(glob,descending=True); top=order[:min(20,len(gids))]
   sims=torch.einsum('pd,ckd->cpk',q[1:],gp[top]); best=sims.max(dim=2).values
   # Best match per distinct query patch, then average top three query patches.
   local=best.topk(3,dim=1).values.mean(dim=1)
   x=torch.stack([glob[top],local],dim=1)
   out.append(dict(sid=s,turn=r['turn'],final=r is rs[-1],instruction=r['instruction'],gids=gids,target=gids.index(hashes[s]),order=order,top=top,x=x,score_seconds=time.perf_counter()-start))
 return out
tr_all=build('train'); va_final=build('val',final_only=True); te=build('test',final_only=True)
tr_first=[r for r in tr_all if r['turn']==min(item['turn'] for item in groups[r['sid']])]
assert tr_first and tr_all and va_final and te
class Fusion(torch.nn.Module):
 def __init__(self):
  super().__init__(); self.net=torch.nn.Sequential(torch.nn.Linear(2,16),torch.nn.ReLU(),torch.nn.Linear(16,1))
 def forward(self,x): return self.net(x).flatten()
# Train only on train originals. Validation chooses epoch; test is never used in fitting.
def train_fusion(name,train_rows):
 started=time.perf_counter()
 torch.manual_seed(seed)
 model=Fusion(); opt=torch.optim.Adam(model.parameters(),lr=.003); bestloss=float('inf'); best=None; training=[]
 for epoch in range(100):
  model.train(); losses=[]
  for r in train_rows:
   hit=(r['top']==r['target']).nonzero().flatten()
   if not len(hit): continue
   loss=F.cross_entropy(model(r['x'])[None],hit[:1]); opt.zero_grad(); loss.backward(); opt.step(); losses.append(float(loss.detach()))
  model.eval()
  with torch.no_grad():
   vals=[float(F.cross_entropy(model(r['x'])[None],(r['top']==r['target']).nonzero().flatten()[:1])) for r in va_final if (r['top']==r['target']).any()]
   vl=float(np.mean(vals)) if vals else float('inf')
  training.append(dict(model=name,epoch=epoch,train_loss=float(np.mean(losses)) if losses else None,val_final_query_loss=vl))
  if vl<bestloss: bestloss=vl; best={k:v.detach().clone() for k,v in model.state_dict().items()}
 if best is None: raise RuntimeError(f'{name}: training could not find any train/validation queries in the SSCD top-20.')
 model.load_state_dict(best); model.eval()
 torch.save({'state_dict':best,'seed':seed,'training_mode':name,'inputs':['global_cosine','top3_distinct_query_patch_matches']},root/f'{name}.pt')
 return model,training,time.perf_counter()-started
single_net,single_log,single_seconds=train_fusion('fusion_single_edit',tr_first)
multi_net,multi_log,multi_seconds=train_fusion('fusion_multistage',tr_all)
pd.DataFrame(single_log+multi_log).to_csv(root/'training_log.csv',index=False)
rankings=[]
preview=root/'preview_images'; preview.mkdir(exist_ok=True)
def save_preview(image,path):
 if path.exists(): return
 image=image.copy(); image.thumbnail((600,600)); image.save(path,format='JPEG',quality=86,optimize=True)
test_gids=sorted({hashes[s] for s in groups if splits[s]=='test'})
for gid in test_gids: save_preview(originals[next(s for s in originals if hashes[s]==gid)],preview/f'source_{gid}.jpg')
for r in te:
 edited=next(row['target'] for row in groups[r['sid']] if row['turn']==r['turn'])
 safe=f"{r['sid']}_turn{r['turn']}"
 save_preview(edited,preview/f'query_{safe}.jpg')
for method in ['sscd_global','sscd_fixed_patch','fusion_single_edit','fusion_multistage']:
 for r in te:
  start=time.perf_counter(); order=r['order'].tolist()
  if method!='sscd_global':
   with torch.no_grad():
    score=.75*r['x'][:,0]+.25*r['x'][:,1] if method=='sscd_fixed_patch' else single_net(r['x']) if method=='fusion_single_edit' else multi_net(r['x'])
   top=r['top'][torch.argsort(score,descending=True)].tolist(); order=top+order[len(top):]
  rankings.append(dict(method=method,query_id=f"{r['sid']}_turn{r['turn']}",turn=r['turn'],last_observed_turn=r['final'],gallery_size=len(r['gids']),target_rank=order.index(r['target'])+1,target_id=r['gids'][r['target']],query_image=f"query_{r['sid']}_turn{r['turn']}.jpg",ranking=json.dumps([r['gids'][j] for j in order]),rerank_seconds=time.perf_counter()-start,instruction=r['instruction']))
df=pd.DataFrame(rankings); df.to_csv(root/'per_query_rankings.csv',index=False)
def summarize(d):
 return dict(queries=len(d),gallery_size=int(d.gallery_size.iloc[0]),**{f'Recall@{k}':float((d.target_rank<=k).mean()) for k in [1,5,10]})
comparison=pd.DataFrame([dict(method=m,**summarize(d)) for m,d in df.groupby('method')]); comparison.to_csv(root/'comparison_table.csv',index=False)
pd.DataFrame([dict(method=m,turn=int(t),**summarize(d)) for (m,t),d in df.groupby(['method','turn'])]).to_csv(root/'by_turn.csv',index=False)
run_record.update(status='measured',stage='complete',seed=seed,device=device,dataset=dataset,dataset_viewer_api=api,config=a.config,split=a.split,bounded_rows=len(rows),complete_sessions=len(groups),dataset_snapshot='Dataset Viewer currently serves the selected split; row indices are recorded in dataset_manifest.csv',checkpoint_url=url,checkpoint_sha256=ckhash,preprocess='resize 288x288, ImageNet normalization',feature_seconds=feature_seconds,training_seconds={'single_edit':single_seconds,'multistage':multi_seconds},trainable_parameters=sum(x.numel() for x in multi_net.parameters()),training_comparison='same fusion MLP; single-edit training uses first edit per training session; multi-stage training uses all observed edits; validation and test use only each session final edit',protocol='first original; exact pixel hash grouping; complete sessions; disjoint galleries; top20 reranking; final-edit-only testing',limitations=['Bounded prefix sample; last complete session only','No external dataset or AI-mask branch evaluated','Small gallery; exact hash checks do not catch near duplicates','No novelty claim'],command='python cloud_experiment_v2.py --root '+str(root)+' --rows '+str(a.rows))
save_run_record(); (root/'requirements_frozen.txt').write_text(subprocess.check_output([sys.executable,'-m','pip','freeze'],text=True))
shutil.copy2(__file__,root/'cloud_experiment_v2.py') if Path(__file__).resolve()!= (root/'cloud_experiment_v2.py').resolve() else None
print('MEASURED RESULTS\n'+comparison.to_string(index=False),flush=True); print('SAVED',root,flush=True)
