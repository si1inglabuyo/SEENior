import { useEffect, useState } from 'react'
import { login, resendLoginCode, verifyLoginCode } from '../api'
import { IconEye, IconEyeOff } from '../icons'

export default function Login({ onSignedIn }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  // Set when the password was right but the account also needs the texted code.
  const [challenge, setChallenge] = useState(null)
  const [code, setCode] = useState('')
  const [wait, setWait] = useState(0)

  // Counts down the pause before another text can be requested.
  useEffect(() => {
    if (wait <= 0) return undefined
    const timer = setTimeout(() => setWait((seconds) => seconds - 1), 1000)
    return () => clearTimeout(timer)
  }, [wait])

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const pending = await login(username.trim(), password)
      if (pending) {
        setChallenge(pending)
        setWait(pending.resend_after)
      } else {
        onSignedIn()
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function submitCode(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await verifyLoginCode(challenge.challenge_id, code)
      onSignedIn()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function resend() {
    setBusy(true)
    setError('')
    try {
      const fresh = await resendLoginCode(challenge.challenge_id)
      setChallenge(fresh)
      setWait(fresh.resend_after)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  function startOver() {
    setChallenge(null)
    setCode('')
    setPassword('')
    setError('')
  }

  if (challenge) {
    return (
      <div className="login-page">
        <form className="login-card" onSubmit={submitCode}>
          <h1>SEENior</h1>
          <p className="login-sub">Enter your verification code</p>
          <p className="login-hint">
            We sent a 6-digit code by text message to {challenge.phone_hint}. It expires in{' '}
            {Math.round(challenge.expires_in / 60)} minutes.
          </p>

          <label htmlFor="code">Verification code</label>
          <input
            id="code"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            autoFocus
            required
          />

          {error && <p className="error">{error}</p>}

          <button type="submit" disabled={busy || code.length !== 6}>
            {busy ? 'Checking…' : 'Verify and sign in'}
          </button>
          <p className="login-hint">
            <button type="button" className="link-button" onClick={resend} disabled={busy || wait > 0}>
              {wait > 0 ? `Send a new code in ${wait}s` : 'Send a new code'}
            </button>
            {' · '}
            <button type="button" className="link-button" onClick={startOver}>
              Back to sign in
            </button>
          </p>
        </form>
      </div>
    )
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit}>
        <h1>SEENior</h1>
        <p className="login-sub">Barangay Responder Dashboard</p>

        {/* Username, not email. Responder credentials are issued by the OSCA officer
            rather than self-registered (spec §2). */}
        <label htmlFor="username">Username</label>
        <input
          id="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />

        <label htmlFor="password">Password</label>
        {/* Hidden until asked for. type="button" so the eye never submits the form. */}
        <div className="password-field">
          <input
            id="password"
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
          <button
            type="button"
            className="password-toggle"
            onClick={() => setShowPassword((shown) => !shown)}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            aria-pressed={showPassword}
          >
            {showPassword ? <IconEyeOff /> : <IconEye />}
          </button>
        </div>

        {error && <p className="error">{error}</p>}

        <button type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="login-hint">
          Accounts are issued by the OSCA officer. There is no sign-up.
        </p>
      </form>
    </div>
  )
}
