import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';

const { createBackend, BEN } = await import(pathToFileURL(process.argv[2]));
let mismatches = 0;
for (let mask = 0; mask < 16; mask++) {
  const backend = createBackend();
  const rules = [['customer', 'read'], ['customer', 'write'], ['contact', 'read'], ['contact', 'write']];
  backend.state.groups.push({ id: 'synthetic-review-group', name: 'Review', members: [backend.ben.id],
    actions: ['ticket.read', 'ticket.edit', 'project.read'].map(action => ({action, projectId: backend.project.id})),
    fields: rules.flatMap(([field, operation], bit) => mask & (1 << bit)
      ? [{resourceType: 'ticket', field, operation, projectId: backend.project.id, effect: 'allow'}] : []) });
  backend.state.helpdesk.customers.push({id:'synthetic-customer',name:'Synthetic',projects:[backend.project.id]});
  backend.state.helpdesk.contacts.push({id:'synthetic-contact',customerId:'synthetic-customer',name:'Synthetic contact',addresses:['contact@example.test']});
  const ticket = backend.addTicket('Synthetic', 'Synthetic', backend.anna);
  let handler, answer;
  await backend.install({addCookies:async()=>{}}, {route:async(pattern, callback)=>{handler=callback;}}, {as:BEN});
  await handler({request:()=>({url:()=>`https://localhost/api/v1/projects/${backend.project.id}/tickets/${ticket.number}/helpdesk/options`,
    headers:()=>({}),postDataBuffer:()=>null,method:()=> 'GET'}),fulfill:async value=>{answer=value;}});
  const expectedStatus = (mask & 3) === 3 ? 200 : 403;
  const expectedContacts = (mask & 12) === 12 ? 1 : 0;
  const contacts = answer.status === 200 ? JSON.parse(answer.body)[0].contacts.length : null;
  const matches = answer.status === expectedStatus && (answer.status !== 200 || contacts === expectedContacts);
  if (!matches) mismatches++;
  console.log(JSON.stringify({mask,status:answer.status,contacts,matches}));
}
console.log(`Mismatches: ${mismatches}/16`);
if (!process.argv.includes('--observe')) assert.equal(mismatches, 0);
