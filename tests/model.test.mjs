import test from 'node:test';
import assert from 'node:assert/strict';
import {initialData,upgradeLocalData,scoreQuestion,questionSet,validateImport,mergePart,buildQuiz,isSafeAssetPath,getStats} from '../app/model.mjs';
const d=initialData();
const optionIds=['A','B','C','D'];
test('fresh revision replaces obsolete 18-question plant demo',()=>{
 assert.equal(d.chapters.length,6);
 assert.equal(d.notes.length,13);
 assert.equal(d.questions.length,48);
 assert.equal(d.images.length,4);
 assert.equal(d.parts.length,8);
 assert.ok(d.questions.every(q=>q.id.startsWith('fresh-')&&q.origin==='generated'));
 assert.ok(!d.chapters.some(q=>q.id.startsWith('demo-')));
});
test('fresh questions have source-backed metadata and unique IDs',()=>{
 assert.equal(new Set(d.questions.map(q=>q.id)).size,d.questions.length);
 assert.equal(new Set(d.notes.map(n=>n.id)).size,d.notes.length);
 assert.ok(d.questions.every(q=>q.sourcePdfPart===2&&q.sourcePage>=1&&q.sourcePage<=150&&q.explanation.length>10));
 assert.ok(d.questions.every(q=>q.options.length===4&&q.correct.length===1&&q.options.some(o=>o.id===q.correct[0])));
 assert.ok(d.questions.every(q=>q.origin==='generated'&&q.status==='verified'));
});
test('single-select scorer checks correct and incorrect answers',()=>{
 const q=d.questions[0];assert.equal(scoreQuestion(q,q.correct),true);
 assert.equal(scoreQuestion(q,optionIds.filter(x=>x!==q.correct[0]).slice(0,1)),false);
 assert.equal(scoreQuestion(q,['A','B']),false);
});
test('image-only filtering includes four new original StudyDock diagrams',()=>{
 const qs=questionSet(d,{type:'image'});assert.equal(qs.length,4);
 assert.ok(qs.every(q=>q.imageIds.length===1));
 assert.ok(qs.every(q=>d.images.some(img=>img.id===q.imageIds[0])));
});
test('source selection distinguishes adapted and original MCQs',()=>{
 assert.equal(questionSet(d,{source:'generated'}).length,48);
 assert.equal(questionSet(d,{source:'textbook'}).length,0);
});
test('quiz builder caps to question pool without repeats',()=>{
 const ids=buildQuiz(d.questions,100,()=>.3);assert.equal(ids.length,48);assert.equal(new Set(ids).size,48);
});
test('rejects unsafe image paths',()=>{
 assert.equal(isSafeAssetPath('assets/fresh-eye-map.svg'),true);
 assert.equal(isSafeAssetPath('../secret.png'),false);
 assert.equal(isSafeAssetPath('https://example.com/img.svg'),false);
});
const part={schemaVersion:1,partId:1,firstPage:1,lastPage:150,chapters:[{id:'ch1',title:'Hello'}],images:[{id:'img1',path:'assets/a.png',alt:'An image',status:'verified'}],notes:[{id:'n1',chapterId:'ch1',title:'Notes',body:'Explanation',imageIds:['img1']}],questions:[{id:'q1',chapterId:'ch1',type:'image',stem:'What is shown?',options:optionIds.map(id=>({id,label:id})),correct:['A'],imageIds:['img1'],status:'verified',origin:'textbook'}]};
test('validates imported part and merges it into one master database',()=>{
 assert.deepEqual(validateImport(part,d).errors,[]);
 const merged=mergePart(d,part);assert.equal(merged.parts[0].status,'imported');assert.equal(merged.parts[0].firstPage,1);
 assert.equal(merged.questions.find(q=>q.id==='q1').partId,1);assert.equal(questionSet(merged,{chapterId:'ch1'}).length,1);
 assert.equal(merged.questions.length,49);
});
test('rejects image questions with missing images',()=>{
 const bad=structuredClone(part);bad.questions[0].imageIds=['not-found'];assert.ok(validateImport(bad,d).errors.some(e=>e.includes('not found')));
});
test('rejects incorrect answer keys',()=>{
 const bad=structuredClone(part);bad.questions[0].correct=['X'];assert.ok(validateImport(bad,d).errors.some(e=>e.includes('invalid single-answer')));
});
test('restricts import part IDs to eight',()=>{
 const bad=structuredClone(part);bad.partId=9;assert.ok(validateImport(bad,d).errors.some(e=>e.includes('partId')));
});
test('legacy demo is removed on upgrade, imported real data remains',()=>{
 const legacy=structuredClone(mergePart(d,part));
 legacy.chapters.push({id:'demo-c1',origin:'demo'});legacy.questions.push({id:'demo-q1',origin:'demo'});
 legacy.notes.push({id:'demo-n1',origin:'demo'});legacy.images.push({id:'demo-img1',origin:'demo'});
 const upgraded=upgradeLocalData(legacy);
 assert.ok(['chapters','questions','notes','images'].every(k=>upgraded[k].every(v=>!v.id.startsWith('demo-'))));
 assert.equal(upgraded.parts[0].status,'imported');assert.ok(upgraded.questions.some(q=>q.id==='q1'));
 assert.ok(upgraded.questions.some(q=>q.id==='fresh-q001'));
});
test('upgrading twice is idempotent, preserves original items and question IDs',()=>{
 const original=mergePart(d,part);
 const once=upgradeLocalData(original);const twice=upgradeLocalData(once);
 assert.equal(once.questions.length,twice.questions.length);
 assert.equal(new Set(twice.questions.map(q=>q.id)).size,twice.questions.length);
 assert.ok(twice.questions.some(q=>q.id==='q1'));
});
test('rejects notes with missing figure references',()=>{
 const bad=structuredClone(part);bad.notes[0].imageIds=['missing'];assert.ok(validateImport(bad,d).errors.some(e=>e.includes('Note n1: image missing not found')));
});
test('original unresolved questions remain unscored',()=>{
 const enriched=structuredClone(part);
 enriched.source={bookTitle:'Verified sample book'};
 enriched.unresolvedOriginalQuestions=[{id:'source-q-unresolved',stem:'Ambiguous question',partId:1,sourcePage:11,status:'unverified',correct:[]}];
 const merged=mergePart(d,enriched);
 assert.equal(merged.unresolvedOriginalQuestions.length,1);
 assert.equal(merged.book.title,'Verified sample book');
 assert.equal(questionSet(merged).length,49);
 assert.ok(!questionSet(merged).some(q=>q.id==='source-q-unresolved'));
});
test('fresh question and import stats are correct',()=>{
 const s=getStats(d,{attempts:[]});assert.equal(s.starterQuestions,48);assert.equal(s.originalQuestions,0);assert.equal(s.questions,48);
});
