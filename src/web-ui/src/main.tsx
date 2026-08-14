import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'
import { HttpIdentityApi } from './features/auth/IdentityApi'
import '@fontsource-variable/noto-sans-kr/wght.css'
import './app.css'

const root = document.getElementById('root')
if (!root) throw new Error('root element is required')

createRoot(root).render(
  <StrictMode>
    <App identityApi={new HttpIdentityApi()} />
  </StrictMode>,
)
