import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import App from './App'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('MesApp frontend', () => {
  it('muestra autenticación y carga empleados desde la API local', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      const payload = path.includes('/auth/employees') ? [] : path.includes('/auth/me') ? { error: 'Inicia sesión' } : { local: 'CONNECTED', pendingSync: 0 }
      const status = path.includes('/auth/me') ? 401 : 200
      return { ok: status === 200, status, json: async () => payload } as Response
    })
    vi.stubGlobal('fetch', fetchMock)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<BrowserRouter><QueryClientProvider client={client}><App /></QueryClientProvider></BrowserRouter>)
    expect(await screen.findByRole('heading', { name: 'Qué bueno verte' })).toBeInTheDocument()
    expect(await screen.findByLabelText('Tu nombre')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/employees', expect.objectContaining({ credentials: 'include' }))
  }, 15000)
})
