import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { Presentation, PresentationFile } from '@oai/artifact-tool';

const workspaceDir = '/Users/viveknegi/Downloads/wallet-service';
const buildDir = path.join(workspaceDir, '.wallet-deck-build');
const outDir = path.join(workspaceDir, 'presentation-output');
const finalPath = path.join(outDir, 'distributed-fintech-wallet-service-final.pptx');
const skillDir = '/Users/viveknegi/.codex/plugins/cache/openai-primary-runtime/presentations/26.909.12148/skills/presentations';
const { resolvePresentationFont, finalizePresentation } = await import(pathToFileURL(path.join(skillDir, 'container_tools/artifact_tool_utils.mjs')).href);
const font = resolvePresentationFont();
const C = { navy:'#101B35', ink:'#18243B', muted:'#53627A', blue:'#2F6FED', teal:'#139E91', amber:'#E9A52B', red:'#D85B5B', bg:'#F5F7FB', white:'#FFFFFF', paleBlue:'#E8EFFD', paleTeal:'#E4F5F2', paleAmber:'#FFF4DD', paleRed:'#FCECEC', line:'#CCD5E3' };
const ppt = Presentation.create({ slideSize: { width: 1280, height: 720 } });

function shape(slide, geometry, x, y, w, h, fill='none', lineFill='none', lineWidth=0, name='') {
  return slide.shapes.add({ geometry, name, position:{left:x,top:y,width:w,height:h}, fill, line:{style:'solid',fill:lineFill,width:lineWidth} });
}
function text(slide, value, x, y, w, h, size=24, color=C.ink, bold=false, opts={}) {
  const s=shape(slide,'textbox',x,y,w,h,'none','none',0,opts.name||''); s.text=value;
  s.text.style={typeface:font,fontSize:size,bold,color,autoFit:'shrinkText',verticalAlignment:opts.valign||'middle',alignment:opts.align||'left',wrap:true};
  return s;
}
function line(slide,x1,y1,x2,y2,color=C.line,width=2){
  return slide.shapes.add({geometry:'line',position:{left:Math.min(x1,x2),top:Math.min(y1,y2),width:Math.max(1,Math.abs(x2-x1)),height:Math.max(1,Math.abs(y2-y1)),horizontalFlip:x2<x1,verticalFlip:y2<y1},fill:'none',line:{style:'solid',fill:color,width}});
}
function box(slide,label,x,y,w,h,fill=C.white,accent=C.blue,size=21){
  const s=shape(slide,'roundRect',x,y,w,h,fill,accent,2); s.text=label;
  s.text.style={typeface:font,fontSize:size,bold:true,color:C.ink,autoFit:'shrinkText',alignment:'center',verticalAlignment:'middle',wrap:true}; return s;
}
function arrow(slide,x,y,w=48,h=22,color=C.blue){ shape(slide,'rightArrow',x,y,w,h,color,'none',0); }
function bullet(slide,label,x,y,w,size=22,color=C.ink,accent=C.blue){
  shape(slide,'ellipse',x,y+11,9,9,accent,'none',0); text(slide,label,x+22,y,w-22,54,size,color,false);
}
function base(title,kicker=''){
  const s=ppt.slides.add(); s.background.fill=C.bg;
  shape(s,'rect',0,0,14,720,C.blue,'none',0);
  if(kicker) text(s,kicker.toUpperCase(),62,34,700,24,15,C.teal,true);
  text(s,title,62,66,1156,68,43,C.navy,true,{name:'slide-title'});
  line(s,62,145,1218,145,C.line,1);
  text(s,String(ppt.slides.items.length).padStart(2,'0'),1178,674,42,20,14,C.muted,true,{align:'right'});
  return s;
}
function notes(slide,txt){ slide.speakerNotes.textFrame.setText(txt); }

// 1. Cover
{
 const s=ppt.slides.add(); s.background.fill=C.navy;
 shape(s,'rect',0,0,17,720,C.teal,'none',0);
 text(s,'DISTRIBUTED FINTECH',72,94,650,26,18,'#8FDCD3',true);
 text(s,'Wallet Service',72,146,820,84,58,C.white,true);
 text(s,'Safe money movement across concurrent requests and service failures',76,253,760,72,27,'#D4DDED',false);
 line(s,76,361,785,361,'#40516F',2);
 text(s,'Java 17  ·  Spring Boot  ·  PostgreSQL  ·  Redis  ·  Kafka',76,393,790,38,21,C.white,true);
 text(s,'Docker  ·  Spring Security  ·  JWT',76,437,660,32,19,'#B8C6DA',false);
 text(s,'Presenter: [Your Name]   |   Mini-Project Presentation',76,574,760,34,19,'#B8C6DA',false);
 text(s,'01',1178,674,42,20,14,'#B8C6DA',true,{align:'right'});
 notes(s,'Introduce the project as a study in preserving financial correctness when requests overlap, clients retry, and components fail. The listed stack is the project context. Replace [Your Name] before presenting.');
}
// 2. Problem
{
 const s=base('Financial transfers face competing failure modes','Problem statement');
 const items=[
  ['Concurrent writes','Two requests read the same balance and both attempt a debit.',C.paleRed,C.red],
  ['Client retries','A timeout hides the outcome, so the client may submit the same intent again.',C.paleAmber,C.amber],
  ['Split commits','The database and Kafka cannot commit together in a simple local transaction.',C.paleBlue,C.blue],
  ['Partial failure','A service may stop after a database commit but before replying or publishing.',C.paleTeal,C.teal],
 ];
 const ys=[186,292,398,504];
 items.forEach((it,i)=>{
   shape(s,'rect',76,ys[i],8,76,it[3],'none',0);
   text(s,it[0],102,ys[i]+2,260,31,23,C.navy,true);
   text(s,it[1],373,ys[i]+1,770,62,21,C.ink,false);
 });
 text(s,'Invariant to protect',76,616,225,28,18,C.teal,true);
 text(s,'A transfer changes both account balances as one durable operation.',302,612,840,34,22,C.navy,true);
 notes(s,'Explain that a timeout is ambiguous: the request may have failed before processing, or the transfer may have committed and only the response was lost. This makes safe retries and durable financial invariants central requirements.');
}
// 3. Architecture
{
 const s=base('Service architecture and transfer data flow','Architecture');
 box(s,'Client\nJWT + idempotency key',68,260,188,104,C.white,C.blue,20);
 box(s,'Gateway',306,260,145,104,C.white,C.blue,21);
 box(s,'Spring Boot\nWallet API',503,245,208,132,C.paleBlue,C.blue,23);
 box(s,'PostgreSQL\naccounts · transfers\noutbox',795,190,225,126,C.white,C.teal,20);
 box(s,'Redis\nretry coordination\nTTL keys',795,353,225,111,C.white,C.amber,20);
 box(s,'Kafka',1082,225,130,76,C.white,C.blue,22);
 box(s,'Consumers\naudit · notification',1055,391,184,88,C.paleTeal,C.teal,18);
 arrow(s,258,300,40,22,C.blue); arrow(s,451,300,42,22,C.blue);
 arrow(s,711,259,65,22,C.teal); arrow(s,711,340,65,22,C.amber);
 arrow(s,1020,240,53,22,C.blue); arrow(s,1140,310,22,67,C.teal);
 text(s,'Commit transfer + outbox atomically',746,160,315,25,16,C.muted,true,{align:'center'});
 text(s,'Redis supports request coordination; PostgreSQL owns balances',712,485,390,28,16,C.muted,false,{align:'center'});
 text(s,'Synchronous financial write',68,556,360,27,18,C.blue,true);
 text(s,'Asynchronous event delivery',796,556,360,27,18,C.teal,true);
 notes(s,'Walk left to right. The API authenticates the user, coordinates duplicate requests, and performs the financial write in PostgreSQL. The same database transaction records an outbox event. A separate publisher delivers that committed event to Kafka for downstream consumers.');
}
// 4. Concurrency
{
 const s=base('Concurrency control: lock accounts in a stable order','Financial correctness');
 text(s,'Opposing transfers',76,177,320,30,22,C.navy,true);
 box(s,'A → B',83,222,160,62,C.paleBlue,C.blue,23); box(s,'B → A',83,319,160,62,C.paleAmber,C.amber,23);
 text(s,'Both acquire locks by sorted account ID',324,180,560,28,20,C.muted,true);
 box(s,'min(A, B)',350,239,190,70,C.white,C.teal,22); arrow(s,548,263,48,22,C.teal); box(s,'max(A, B)',600,239,190,70,C.white,C.teal,22);
 line(s,243,253,321,253,C.blue,2); line(s,243,349,287,349,C.amber,2); line(s,287,349,287,274,C.amber,2); line(s,287,274,321,274,C.amber,2);
 text(s,'One transaction',850,193,260,26,19,C.blue,true);
 bullet(s,'Lock rows with SELECT … FOR UPDATE',850,226,360,19);
 bullet(s,'Check funds after acquiring locks',850,286,360,19);
 bullet(s,'Debit and credit, then commit atomically',850,346,360,19);
 line(s,76,437,1204,437,C.line,1);
 text(s,'Pessimistic locking',80,465,310,29,22,C.navy,true);
 text(s,'Strong fit for conflicting financial writes. Waits can throttle hot accounts.',80,501,490,72,19,C.ink,false);
 text(s,'Optimistic locking',654,465,310,29,22,C.navy,true);
 text(s,'Version checks avoid long locks, but conflicts need retries under contention.',654,501,490,72,19,C.ink,false);
 text(s,'AOP note: @Transactional advice applies through the Spring proxy; self-invocation can bypass it.',80,610,1100,34,18,C.teal,true);
 notes(s,'Explain the classic deadlock: one transfer locks A then waits for B while another holds B and waits for A. Sorting account identifiers makes both acquire locks in the same order, removing that cycle for this access path. It does not eliminate every possible database deadlock, so production code should handle transient failures. Contrast pessimistic locking with optimistic version checks. Mention that Spring transaction advice is proxy based and self-invocation can bypass it.');
}
// 5. Idempotency
{
 const s=base('Idempotency: retries map to one logical transfer','API reliability');
 box(s,'Request 1\nkey: k-123',76,239,190,88,C.white,C.blue,21);
 box(s,'Request 2\nkey: k-123',76,365,190,88,C.white,C.amber,21);
 box(s,'Redis\nSET key if absent\nTTL',367,294,210,112,C.paleAmber,C.amber,20);
 box(s,'Transfer service',682,294,205,112,C.paleBlue,C.blue,21);
 box(s,'PostgreSQL\nunique client + key\ntransfer result',987,294,224,112,C.paleTeal,C.teal,19);
 arrow(s,269,272,70,22,C.blue); arrow(s,269,397,70,22,C.amber); arrow(s,580,338,92,22,C.blue); arrow(s,890,338,88,22,C.teal);
 text(s,'First request proceeds',371,445,210,26,17,C.teal,true,{align:'center'});
 text(s,'Duplicate returns the original outcome',651,445,285,26,17,C.muted,true,{align:'center'});
 line(s,76,514,1204,514,C.line,1);
 text(s,'Design details',78,543,220,28,20,C.navy,true);
 text(s,'Scope key by client  ·  fingerprint request body  ·  define in-progress behavior  ·  persist uniqueness in PostgreSQL',78,582,1100,52,19,C.ink,false);
 notes(s,'Use a concrete retry example: the client times out and repeats the request with the same key. Redis can quickly coordinate duplicate attempts with an expiring key, but the database should enforce a durable unique constraint as the final correctness guard. A request fingerprint prevents accidental reuse of one key for different transfer parameters.');
}
// 6. Outbox
{
 const s=base('Transactional outbox: reliable event delivery','Reliable messaging');
 text(s,'Unsafe dual write',76,180,270,28,20,C.red,true);
 box(s,'Commit database',80,222,205,66,C.white,C.red,20); box(s,'Publish Kafka',80,326,205,66,C.white,C.red,20);
 text(s,'Either step can fail after the other succeeds',79,418,260,53,18,C.muted,false);
 line(s,372,179,372,542,C.line,1);
 text(s,'Atomic database transaction',422,180,520,28,20,C.teal,true);
 box(s,'Update balances\n+ transfer row',422,232,220,86,C.paleBlue,C.blue,20);
 box(s,'Insert outbox event',422,359,220,72,C.paleTeal,C.teal,20);
 line(s,532,318,532,351,C.teal,2);
 box(s,'Poller / CDC',711,286,175,82,C.white,C.amber,20);
 box(s,'Kafka',944,286,125,82,C.white,C.blue,22);
 box(s,'Audit / notifications',1095,286,140,82,C.paleTeal,C.teal,17);
 arrow(s,647,310,58,22,C.teal); arrow(s,889,310,48,22,C.blue); arrow(s,1070,310,22,22,C.teal);
 text(s,'Delivery is at least once',711,416,370,28,20,C.navy,true,{align:'center'});
 text(s,'Consumers deduplicate by event or transfer ID',711,451,440,42,18,C.muted,false,{align:'center'});
 text(s,'Polling is simple to operate; CDC can reduce polling overhead at greater operational complexity.',422,554,785,55,19,C.ink,false);
 notes(s,'Describe the dual-write gap. The transfer, balance changes, and outbox row commit together in PostgreSQL. A publisher later sends the event. If it crashes after Kafka accepts the event but before marking the row delivered, it may publish again. That is at-least-once delivery, so consumers must be idempotent. Debezium CDC is a roadmap option, not a free reliability upgrade.');
}
// 7. Security
{
 const s=base('Security and resilience at the API boundary','Access control');
 box(s,'JWT',76,247,128,74,C.white,C.blue,23); arrow(s,212,273,54,22,C.blue);
 box(s,'Spring Security\nfilter chain',274,235,208,98,C.paleBlue,C.blue,20); arrow(s,491,273,51,22,C.blue);
 box(s,'Authorization\nuser ↔ wallet',551,235,204,98,C.white,C.teal,19); arrow(s,763,273,51,22,C.teal);
 box(s,'Token bucket\nRedis counters',822,235,196,98,C.paleAmber,C.amber,19); arrow(s,1025,273,51,22,C.blue);
 box(s,'Wallet API',1082,247,132,74,C.white,C.blue,20);
 text(s,'JWT validation authenticates the caller. Wallet-level checks authorize the transfer.',77,385,1090,39,22,C.navy,true);
 line(s,76,458,1204,458,C.line,1);
 text(s,'Token bucket policy',80,489,258,29,21,C.navy,true);
 text(s,'Allows short bursts while limiting average request rate. Apply per user or client.',80,530,475,54,19,C.ink,false);
 text(s,'Redis outage policy',654,489,258,29,21,C.navy,true);
 text(s,'Fail open favors availability. Fail closed protects capacity but can reject valid requests.',654,530,495,54,19,C.ink,false);
 notes(s,'Walk through the filter chain: validate token signature and claims, then authorize the caller against the source wallet. Rate limiting is a separate resilience control. A token bucket permits bursts while bounding the long-run rate. Distributed counters require atomic Redis operations and a deliberate fail-open or fail-closed policy.');
}
// 8. Testing
{
 const s=base('Testing strategy: assert invariants under contention','Verification');
 text(s,'TEST LAYERS',76,186,220,25,16,C.teal,true);
 line(s,123,545,1155,545,C.line,3);
 const stages=[
  {x:100,w:265,title:'Unit',sub:'JUnit 5 + Mockito',detail:'Business rules\ninsufficient funds\nerror mapping',color:C.blue},
  {x:446,w:340,title:'Integration',sub:'Real database boundaries',detail:'Transactions\nconstraints\nrow-lock behavior',color:C.teal},
  {x:838,w:340,title:'Concurrency + load',sub:'Threads and connection pools',detail:'Overlapping transfers\nduplicate retries\nlatency percentiles',color:C.amber},
 ];
 stages.forEach(st=>{ shape(s,'rect',st.x,246,st.w,8,st.color,'none',0); text(s,st.title,st.x,278,st.w,33,25,C.navy,true); text(s,st.sub,st.x,319,st.w,28,18,st.color,true); text(s,st.detail,st.x,365,st.w,111,20,C.ink,false); shape(s,'ellipse',st.x+10,533,26,26,st.color,'none',0); });
 text(s,'Invariant: completed transfers conserve value and never debit below the permitted balance.',78,590,1120,38,21,C.navy,true);
 text(s,'Report workload, hardware, concurrency, and p95/p99 latency with any throughput result.',78,631,1100,27,17,C.muted,false);
 notes(s,'Describe the test pyramid and stress that unit tests alone cannot validate PostgreSQL row locking. Run concurrency scenarios against the database engine used by the service. Assert value conservation and balance bounds. No benchmark result is claimed in this deck; report throughput only after measuring a specified workload, hardware, pool size, and latency percentiles.');
}
// 9. Shortcomings
{
 const s=base('Known limits and the next engineering steps','Trade-offs and roadmap');
 const rows=[
  ['Hot account locks','Writes to one wallet serialize','Profile contention; define workload limits'],
  ['Outbox polling','Database queries add load and delivery lag','Tune batches; evaluate Debezium CDC'],
  ['Shared dependencies','PostgreSQL, Redis, Kafka need failover','Deploy replication, alerts, recovery drills'],
  ['Cross-shard transfers','Partitioning complicates atomic movement','Shard only after measured pressure'],
 ];
 text(s,'PRESSURE POINT',78,184,280,24,15,C.muted,true); text(s,'WHY IT MATTERS',444,184,330,24,15,C.muted,true); text(s,'NEXT STEP',841,184,320,24,15,C.muted,true);
 line(s,76,218,1204,218,C.navy,2);
 rows.forEach((r,i)=>{const y=241+i*87; text(s,r[0],78,y,330,36,21,C.navy,true); text(s,r[1],444,y,354,52,18,C.ink,false); text(s,r[2],841,y,355,52,18,C.teal,false); if(i<3) line(s,76,y+65,1204,y+65,C.line,1);});
 text(s,'Horizontal API scaling does not remove contention on shared account rows.',78,603,1100,36,21,C.blue,true);
 notes(s,'Be explicit that database row locking protects correctness but constrains writes to hot accounts. Horizontal API scaling helps stateless request handling, not conflicting updates to one row. CDC can lower polling overhead but adds operational dependencies. Sharding is a later option because cross-shard transfers become much harder to coordinate atomically.');
}
// 10. Conclusion
{
 const s=ppt.slides.add(); s.background.fill=C.navy;
 shape(s,'rect',0,0,17,720,C.teal,'none',0);
 text(s,'CONCLUSION',76,72,400,28,18,'#8FDCD3',true);
 text(s,'A safe transfer is a coordinated set of guarantees',76,126,1030,82,44,C.white,true);
 const steps=[['01','Authenticate'],['02','Deduplicate'],['03','Lock + transact'],['04','Record outbox'],['05','Publish safely']];
 steps.forEach((st,i)=>{const x=76+i*226; text(s,st[0],x,293,54,27,17,'#8FDCD3',true); text(s,st[1],x,335,198,40,21,C.white,true); if(i<4) arrow(s,x+174,347,36,18,'#5A769C');});
 line(s,76,425,1184,425,'#40516F',2);
 text(s,'Correctness lives in durable state. Retries and event delivery need explicit contracts.',76,464,1040,66,24,'#D4DDED',false);
 text(s,'Questions & discussion',76,582,560,56,34,C.white,true);
 text(s,'10',1178,674,42,20,14,'#B8C6DA',true,{align:'right'});
 notes(s,'Close by summarizing the transfer lifecycle: authenticate and authorize, deduplicate retries, lock and update both accounts atomically, record event intent in the outbox, then publish with at-least-once semantics. Invite questions about lock contention, idempotency guarantees, and the outbox trade-off.');
}

await fs.mkdir(buildDir,{recursive:true}); await fs.mkdir(outDir,{recursive:true});
const candidatePath=path.join(buildDir,'candidate.pptx');
await (await PresentationFile.exportPptx(ppt)).save(candidatePath);
const result=await finalizePresentation({
  explicitTotalSlideCount:10,
  requiredNativeTableOwnerSlides:[],
  requiredNativeChartOwnerSlides:[],
  workspaceDir,candidatePath,finalPath,
  pythonExecutable:process.env.RUNTIME_PYTHON,
  integrityValidatorPath:path.join(skillDir,'container_tools/inspect_presentation_package_integrity.py'),
  layoutValidatorPath:path.join(skillDir,'container_tools/inspect_presentation_layout_geometry.py'),
  layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-heading-fit'],
  fontPolicy:{basis:'design',families:[font]},
  verifyArtifactToolImport:true,
  receiptPath:path.join(buildDir,'validation-final.json'),
});
console.log(JSON.stringify({finalPath,font,result},null,2));
