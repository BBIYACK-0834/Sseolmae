import { useState } from 'react'
import { request } from './api'

const providers = [['google', '구글'], ['kakao', '카카오'], ['naver', '네이버']]

export default function Login({ session }) {
  const [signup, setSignup] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  function switchMode() {
    setSignup(!signup)
    setPassword('')
    setMessage('')
    setError('')
  }
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setError('')
    setMessage('')
    setBusy(true)
    try {
      if (signup) {
        await request('/auth/register', {method:'POST', csrf:session.csrf, body:{email:email.trim(),password}})
        setSignup(false)
        setPassword('')
        setMessage('가입을 완료했습니다. 이메일과 비밀번호로 로그인해 주세요.')
      } else {
        await request('/auth/login', {method:'POST', csrf:session.csrf, body:new URLSearchParams({email:email.trim(),password})})
        const next = sessionStorage.getItem('payment-return') ? 'result' : location.hash === '#mine' ? 'mine' : 'booking'
        location.replace('/#' + next)
        location.reload()
      }
    } catch(e) { setError(e.message) }
    finally { setBusy(false) }
  }
  return <section className="panel login auth-panel">
    <h2>{signup ? '회원가입' : '로그인'}</h2>
    <form onSubmit={submit}>
      <h3>{signup ? '이메일로 가입' : '기본 로그인'}</h3>
      <fieldset disabled={busy}>
        <label>이메일<input type="email" autoComplete="username" required maxLength="254" value={email} onChange={e=>setEmail(e.target.value)}/></label>
        <label>비밀번호<input type="password" autoComplete={signup?'new-password':'current-password'} required minLength={signup?10:undefined} maxLength="128" value={password} onChange={e=>setPassword(e.target.value)}/></label>
        {signup && <p className="hint">비밀번호는 10~128자로 입력해 주세요.</p>}
        {error && <p role="alert" className="notice">{error}</p>}
        {message && <p role="status">{message}</p>}
        <button className="primary-auth" type="submit">{busy?'처리 중…':signup?'회원가입':'이메일로 로그인'}</button>
        <button className="auth-switch" type="button" onClick={switchMode}>{signup?'로그인으로 돌아가기':'이메일 회원가입'}</button>
      </fieldset>
    </form>
    {!signup && <div className="social-auth"><h3>소셜 로그인</h3>
      {providers.map(([id,name]) => session.oauthProviders?.includes(id)
        ? <a key={id} className={'button social '+id} href={'/oauth2/authorization/'+id}>{name}로 계속하기</a>
        : <button key={id} type="button" className="social unavailable" disabled>{name} 로그인 · 준비 중</button>)}
    </div>}
  </section>
}
