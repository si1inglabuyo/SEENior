// The guided walkthrough of ten simultaneous alerts. Each step makes the same calls the dashboard
// buttons make, so the real screens update as it plays. Sample seniors and an illustrative order.
import { responderOf } from './mockServer'

const reached = (id, pook) => ['dispatch', id, { action: 'send_team', team: responderOf(pook) }]
const unavailable = (id, pook) => ['dispatch', id, { action: 'responder_unavailable', team: responderOf(pook) }]
const send = (id, team) => ['dispatch', id, { action: 'send_team', team }]
const help = (id, service) => ['dispatch', id, { action: 'request_help', service }]
const call = (id, result) => ['dispatch', id, { action: 'call_result', result }]
const close = (id) => ['resolve', id]

export const SCENARIO = [
  {
    say: 'Ten alerts land within nine minutes, four of them in Pook Libis. The list is already in tier order, and the oldest comes first inside each tier.',
    calls: [],
  },
  {
    say: `Tier 1, SOS: Remedios came first, so the desk phones the Libis responder, ${responderOf('Libis')}, who is on the way. Ernesto is also in Libis and that responder is out, so Tanod team 1 goes from the hall.`,
    calls: [reached('s1', 'Libis'), send('s2', 'Tanod team 1')],
  },
  {
    say: `Tier 2, falls: the Amorsolo responder goes to Teodoro. The Dagohoy responder cannot come, so Tanod team 2 goes to Corazon. Rogelio is in Libis too: the BHERT team goes, and QCDRRMO is called as well.`,
    calls: [
      reached('f1', 'Amorsolo'),
      unavailable('f2', 'Dagohoy'),
      send('f2', 'Tanod team 2'),
      send('f3', 'BHERT team'),
      help('f3', 'QCDRRMO'),
    ],
  },
  {
    say: 'Tier 3: the family asked for a welfare check on Milagros, so the Palaris responder goes.',
    calls: [reached('r1', 'Palaris')],
  },
  {
    say: 'Tier 4, anomalies: call first. Luz, Nenita and Pedro are fine. Felipe does not answer, so his alert turns red and moves up the list.',
    calls: [call('a1', 'cleared'), call('o1', 'cleared'), call('o2', 'cleared'), call('a2', 'no_answer')],
  },
  {
    say: 'The three cleared alerts are closed. The Aguinaldo responder goes to Felipe, the last senior still waiting.',
    calls: [close('a1'), close('o1'), close('o2'), reached('a2', 'Aguinaldo')],
  },
  {
    say: 'Every open alert now has someone on the way, and each step is on its timeline. If alerts outnumber responders, the rest stay red in priority order until someone is free, with the hotlines one click away.',
    calls: [],
  },
]

export async function playStep({ calls }) {
  for (const [kind, id, body] of calls) {
    const dispatching = kind === 'dispatch'
    // A full address, so the mock server matches it wherever this page is opened from.
    await fetch(`https://preview.invalid/barangay/alerts/${id}/${kind}`, {
      method: dispatching ? 'POST' : 'PATCH',
      body: JSON.stringify(dispatching ? body : { notes: 'Called, senior fine' }),
    })
  }
}
