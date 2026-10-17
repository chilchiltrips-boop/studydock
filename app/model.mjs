import {starter} from './seed.mjs';
export const deepClone=x=>JSON.parse(JSON.stringify(x));
export function initialData(){return deepClone(starter)}
export function upgradeLocalData(stored){
  // Migrate pre-v0.3 local installs without erasing imported original textbook data.
  const fresh=initialData();
  if(!stored||typeof stored!=='object'||!Array.isArray(stored.questions))return fresh;
  const next=deepClone(stored);
  for(const key of ['chapters','notes','questions','images']){
    const old=Array.isArray(next[key])?next[key]:[];
    const preserved=old.filter(item=>item && item.origin!=='demo' && !String(item.id||'').startsWith('demo-'));
    const byId=new Map(preserved.map(item=>[item.id,item]));
    // Replace only StudyDock's own starter items. Preserve every imported book ID.
    for(const item of fresh[key]) if(!byId.has(item.id)||String(item.id).startsWith('fresh-')) byId.set(item.id,item);
    next[key]=[...byId.values()];
  }
  if(!Array.isArray(next.parts)||next.parts.length!==8)next.parts=fresh.parts;
  if(!next.book||next.book.id==='demo-science')next.book=fresh.book;
  next.starterRevision=fresh.starterRevision;
  return next;
}
export function scoreQuestion(q,selected){return Array.isArray(selected)&&selected.length===q.correct.length&&[...selected].sort().join('|')===[...q.correct].sort().join('|')}
export function normaliseQuestion(q){return {...q,answerMode:q.answerMode||'single',type:q.type||'mcq',imageIds:q.imageIds||[],origin:q.origin||'imported',status:q.status||'unverified',partId:Number(q.partId)}}
export function validateImport(payload,current){
  const errors=[];
  if(!payload||typeof payload!=='object'||Array.isArray(payload))return {errors:['Expected a JSON object.']};
  if(payload.schemaVersion!==1)errors.push('schemaVersion must be 1.');
  const n=Number(payload.partId);
  if(!Number.isInteger(n)||n<1||n>8)errors.push('partId must be an integer between 1 and 8.');
  for(const k of ['chapters','notes','questions','images'])if(!Array.isArray(payload[k]))errors.push(`${k} must be an array.`);
  if(errors.length)return {errors};
  const ids=new Set(); const imageIds=new Set([...current.images.map(x=>x.id),...payload.images.map(x=>x.id)]);
  const chapters=new Set([...current.chapters.filter(c=>c.origin!=='demo').map(x=>x.id),...payload.chapters.map(x=>x.id)]);
  for(const col of ['chapters','notes','questions','images']){
    for(const item of payload[col]){
      if(!item||typeof item.id!=='string'||!item.id.trim()){errors.push(`${col}: every item requires a non-empty id.`);continue;}
      const key=`${col}:${item.id}`;if(ids.has(key))errors.push(`Duplicate ID in import: ${key}`);ids.add(key);
      if(col==='images'){
        if(typeof item.path!=='string'||!item.path.trim())errors.push(`Image ${item.id}: missing path.`);
        if(typeof item.path==='string'&&!isSafeAssetPath(item.path))errors.push(`Image ${item.id}: path must be a relative assets/... path or image data URL.`);
      }
      if(col==='notes'&&!chapters.has(item.chapterId))errors.push(`Note ${item.id}: chapter ${item.chapterId} not found.`);
      if(col==='questions'){
        if(!chapters.has(item.chapterId))errors.push(`Question ${item.id}: chapter ${item.chapterId} not found.`);
        if(!['mcq','image'].includes(item.type))errors.push(`Question ${item.id}: type must be mcq or image.`);
        if(typeof item.stem!=='string'||!item.stem.trim())errors.push(`Question ${item.id}: missing question text.`);
        if(!Array.isArray(item.options)||item.options.length!==4)errors.push(`Question ${item.id}: requires four options.`);
        if(!Array.isArray(item.correct)||item.correct.length!==1||!item.options?.some(x=>x.id===item.correct[0]))errors.push(`Question ${item.id}: invalid single-answer key.`);
        if(item.type==='image'&&(!Array.isArray(item.imageIds)||!item.imageIds.length))errors.push(`Image question ${item.id}: missing imageIds.`);
        for(const img of item.imageIds||[])if(!imageIds.has(img))errors.push(`Question ${item.id}: image ${img} not found.`);
      }
    }
  }
  for(const i of payload.images){if(i.status==='verified'&&!i.alt)errors.push(`Image ${i.id}: alt text required for verified status.`)}
  for(const note of payload.notes){for(const img of note.imageIds||[])if(!imageIds.has(img))errors.push(`Note ${note.id}: image ${img} not found.`)}
  return {errors,partId:n,counts:{chapters:payload.chapters.length,notes:payload.notes.length,questions:payload.questions.length,images:payload.images.length}};
}
export function isSafeAssetPath(p){return /^assets\/[A-Za-z0-9_./-]+\.(svg|png|jpg|jpeg|webp)$/i.test(p)&&!p.includes('..')||/^data:image\/(png|jpeg|webp|svg\+xml);base64,[a-z0-9+/=]+$/i.test(p)}
export function mergePart(current,payload){
 const check=validateImport(payload,current);if(check.errors.length)throw new Error(check.errors.join('\n'));
 const next=deepClone(current), partId=check.partId;
 // Non-scoreable originals remain accessible for review, with no fabricated answer keys.
 if (payload.unresolvedOriginalQuestions) {
   next.unresolvedOriginalQuestions=[...(next.unresolvedOriginalQuestions||[]).filter(x=>x.sourcePdfPart!==partId && x.partId!==partId),...payload.unresolvedOriginalQuestions.map(x=>({...x,partId}))];
 }
 if(payload.source?.bookTitle) next.book={...next.book,title:payload.source.bookTitle};
 // Keep demo content completely separate from a real imported textbook.
 if (!next.parts.some(p=>p.status==='imported')) {
   for (const key of ['chapters','notes','questions','images']) next[key]=next[key].filter(item=>!item.id.startsWith('demo-'));
 }
 for(const key of ['chapters','notes','questions','images']){
   const collection=new Map(next[key].map(item=>[item.id,item]));
   for(const item of payload[key])collection.set(item.id,{...item,...((key==='questions'||key==='chapters')?{partId}:{}),origin:item.origin||'imported'});
   next[key]=[...collection.values()];
 }
 const i=next.parts.findIndex(p=>p.id===partId);next.parts[i]={...next.parts[i],status:'imported',firstPage:payload.firstPage??null,lastPage:payload.lastPage??null};
 return next;
}
export const eligible=q=>q.status==='verified'&&q.correct?.length===1&&(!q.imageIds?.length||q.imageIds.length>0);
export function questionSet(data,{chapterId='all',type='all',difficulty='all',source='all',wrongIds=[]}={}){return data.questions.filter(q=>eligible(q)&&(chapterId==='all'||q.chapterId===chapterId)&&(type==='all'||q.type===type)&&(difficulty==='all'||q.difficulty===difficulty)&&(source==='all'||(source==='wrong'?wrongIds.includes(q.id):q.origin===source)))}
export function buildQuiz(questions,count=10,seed=Math.random){const shuffled=[...questions];for(let i=shuffled.length-1;i>0;i--){const j=Math.floor(seed()*(i+1));[shuffled[i],shuffled[j]]=[shuffled[j],shuffled[i]]}return shuffled.slice(0,Math.min(count,shuffled.length)).map(q=>q.id)}
export function getStats(data,progress){
 const imported=data.parts.filter(p=>p.status==='imported').length,attempts=progress.attempts||[];
 return {imported,
   chapters:data.chapters.filter(c=>c.origin!=='demo').length,
   questions:data.questions.filter(q=>eligible(q)).length,
   starterQuestions:data.questions.filter(q=>q.partId===0&&q.origin==='generated'&&eligible(q)).length,
   originalQuestions:data.questions.filter(q=>q.origin==='textbook'&&eligible(q)).length,
   attempts:attempts.length,
   best:attempts.length?Math.max(...attempts.filter(a=>a.total>0).map(a=>Math.round(a.correct/a.total*100))):null};
}
