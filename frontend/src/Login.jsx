import { useState } from 'react'
import { request } from './api'

const providers = [['google', '구글'], ['kakao', '카카오'], ['naver', '네이버']]

export default function Login({ session, signup = false }) {
  const [confirmation, setConfirmation] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setError('')
    setBusy(true)
    try {
      if (signup) {
        if (password !== confirmation) throw new Error('비밀번호 확인이 일치하지 않습니다.')
        await request('/auth/register', {method:'POST', csrf:session.csrf, body:{email:email.trim(),password}})
        setConfirmation('')
        setPassword('')
        location.hash = 'login-after-signup'
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
        {signup && <label>비밀번호 확인<input type="password" required autoComplete="new-password" value={confirmation} onChange={e=>setConfirmation(e.target.value)}/></label>}
        {signup && <p className="hint">비밀번호는 10~128자로 입력해 주세요.</p>}
        {error && <p role="alert" className="notice">{error}</p>}
        {location.hash === '#login-after-signup' && <p role="status">가입 요청을 처리했습니다. 이메일과 비밀번호로 로그인해 주세요.</p>}
        <button className="primary-auth" type="submit">{busy?'처리 중…':signup?'회원가입':'이메일로 로그인'}</button>
        <a className="auth-switch" href={signup?'#login':'#signup'}>{signup?'로그인으로 돌아가기':'이메일 회원가입'}</a>
      </fieldset>
    </form>
    {!signup && <div className="social-auth"><h3>소셜 로그인</h3>
      {providers.map(([id,name]) => session.oauthProviders?.includes(id)
        ? <a key={id} className={'button social '+id} href={'/oauth2/authorization/'+id}>{name}로 계속하기</a>
        : <button key={id} type="button" className="social unavailable" disabled>{name} 로그인 · 준비 중</button>)}
    </div>}
  </section>
}
