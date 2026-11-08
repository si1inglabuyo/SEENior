import { useState } from 'react'
import { DEFAULT_SERVICES, DEFAULT_TEAMS, RETIRED_SERVICES, responderProblem } from '../dispatch'

const KEY = 'seenior.responders'

function loadLists() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY))
    if (Array.isArray(saved?.teams) && Array.isArray(saved?.services)) {
      return { ...saved, services: saved.services.filter((name) => !RETIRED_SERVICES.includes(name)) }
    }
  } catch {
    /* unreadable or blocked storage -- fall through to the starting lists */
  }
  return { teams: DEFAULT_TEAMS, services: DEFAULT_SERVICES }
}

// The hall teams and outside services an admin can send. Kept in this browser until the cloud
// has somewhere to hold them, which needs a table from the main lane. `kind` is 'teams' or
// 'services'. add and rename return why a name was refused, or '' when it was saved.
export function useResponders() {
  const [lists, setLists] = useState(loadLists)

  function save(next) {
    setLists(next)
    try {
      localStorage.setItem(KEY, JSON.stringify(next))
    } catch {
      /* ignore -- the in-memory list still applies for this session */
    }
  }

  const others = (except) => [...lists.teams, ...lists.services].filter((name) => name !== except)

  function add(kind, name) {
    const problem = responderProblem(name, others())
    if (!problem) save({ ...lists, [kind]: [...lists[kind], name.trim()] })
    return problem
  }

  function rename(kind, oldName, name) {
    const problem = responderProblem(name, others(oldName))
    if (!problem) {
      save({ ...lists, [kind]: lists[kind].map((n) => (n === oldName ? name.trim() : n)) })
    }
    return problem
  }

  function remove(kind, name) {
    save({ ...lists, [kind]: lists[kind].filter((n) => n !== name) })
  }

  return { ...lists, add, rename, remove }
}
