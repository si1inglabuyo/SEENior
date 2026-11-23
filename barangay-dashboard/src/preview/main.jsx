import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '../index.css'
import { installMockServer } from './mockServer'
import Preview from './Preview'

// Before the first render, so no component can reach the real API.
installMockServer()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <Preview />
  </StrictMode>
)
