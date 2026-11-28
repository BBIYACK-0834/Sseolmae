import { createRoot } from 'react-dom/client'
import { useEffect, useState } from 'react'
import { request } from './api'
import ReservationForm, {
  Login,
  MyBookings,
  PaymentReturn,
} from './ReservationForm'
import './styles.css'

const navigation = [
  ['experiences', '즐길 거리'],
  ['visit', '방문 안내'],
  ['booking', '이용권 예매'],
  ['mine', '내 예약'],
]
const features = [
  ['media-art', '미디어아트', '빛과 영상, 소리가 만드는 실내 전시.'],
  ['snow-sled', '눈썰매장', '눈 위를 달리는 겨울 야외 체험.'],
]
const section = () => location.hash.slice(1) || 'home'

function readPaymentReturn() {
  if (location.pathname.startsWith('/payment/')) {
    const query = new URLSearchParams(location.search)
    const result = {
      failed: location.pathname.endsWith('/fail'),
      approval: {
        orderId: query.get('orderId'),
        paymentKey: query.get('paymentKey'),
        amount: Number(query.get('amount')),
      },
    }
    sessionStorage.setItem('payment-return', JSON.stringify(result))
    history.replaceState(null, '', '/#result')
    return result
  }
  const saved = sessionStorage.getItem('payment-return')
  return saved ? JSON.parse(saved) : null
}

function App() {
  const [result, setResult] = useState(readPaymentReturn)
  const [route, setRoute] = useState(section)
  const [session, setSession] = useState(null)
  const [error, setError] = useState('')
  const [catalog, setCatalog] = useState([])
  useEffect(() => {
    const sync = () => setRoute(section())
    window.addEventListener('hashchange', sync)
    request('/session')
      .then(setSession)
      .catch((e) => setError(e.message))
    request('/experiences')
      .then(setCatalog)
      .catch(() => {})
    return () => window.removeEventListener('hashchange', sync)
  }, [])
  useEffect(() => {
    if (route === 'experiences' || route === 'visit')
      document.getElementById(route)?.scrollIntoView()
    else window.scrollTo(0, 0)
  }, [route])
  const home = ['home', 'experiences', 'visit'].includes(route)
  async function logout() {
    try {
      await request('/logout', { method: 'POST', csrf: session.csrf })
      sessionStorage.removeItem('payment-return')
      location.replace('/#home')
      location.reload()
    } catch (e) {
      setError(e.message)
    }
  }
  return (
    <>
      <a
        className="skip"
        href="#content"
        onClick={(e) => {
          e.preventDefault()
          document.getElementById('content')?.focus()
        }}
      >
        본문 바로가기
      </a>
      <header>
        <div className="wrap navbar">
          <a className="wordmark" href="#home">
            <span className="mark" aria-hidden="true">
              ✳
            </span>{' '}
            설매 <small>SEOLMAE</small>
          </a>
          <nav aria-label="주 메뉴">
            {navigation.map(([key, name]) => (
              <a key={key} href={'#' + key}>
                {name}
              </a>
            ))}
          </nav>
          {session?.userId ? (
            <button className="account" onClick={logout}>
              로그아웃
            </button>
          ) : (
            <a className="account" href="#login">
              로그인
            </a>
          )}
        </div>
      </header>
      <main id="content" tabIndex={-1}>
        {(error ||
          new URLSearchParams(location.search).get('login') === 'failed') && (
          <p className="notice wrap" role="alert">
            {error ||
              '카카오 로그인을 완료하지 못했습니다. 다시 시도해 주세요.'}
          </p>
        )}
        {home ? (
          <>
            <section className="hero">
              <div className="wrap hero-copy">
                <p>미디어아트 전시 · 눈썰매장</p>
                <h1>
                  빛 속을 걷고,
                  <br />눈 위를 달리다.
                </h1>
                <a className="button frost" href="#booking">
                  이용권 예매 <span>↗</span>
                </a>
              </div>
            </section>
            <div className="quick wrap">
              {[
                ['booking', '이용권 예매'],
                ['experiences', '체험 둘러보기'],
                ['visit', '운영 안내'],
                ['mine', '내 예약 확인'],
              ].map(([key, name]) => (
                <a key={key} href={'#' + key}>
                  {name}
                  <span aria-hidden="true">↗</span>
                </a>
              ))}
            </div>
            <section id="experiences" className="wrap block">
              <h2>두 가지 즐길 거리</h2>
              <div className="cards">
                {features.map(([id, fallback, desc], i) => {
                  const item = catalog.find((e) => e.id === id)
                  return (
                    <article className={'experience card-' + i} key={id}>
                      <div className="scene" aria-hidden="true" />
                      <div>
                        <h3>{item?.title || fallback}</h3>
                        <p>{item?.description || desc}</p>
                        <a href="#booking">회차 확인 →</a>
                      </div>
                    </article>
                  )
                })}
              </div>
            </section>
            <section id="visit" className="wrap block">
              <h2>방문 전 확인</h2>
              <div className="info-grid">
                {[
                  [
                    '운영 일정',
                    '판매 중인 날짜와 회차는 예매 화면에서 확인하세요.',
                  ],
                  [
                    '이용 요금',
                    '이용권별 1인 요금과 결제 금액은 회차 선택 후 표시됩니다.',
                  ],
                  ['오시는 길', '시설 주소와 주차 정보는 추후 안내합니다.'],
                ].map(([s, d]) => (
                  <article key={s}>
                    <h3>{s}</h3>
                    <p>{d}</p>
                  </article>
                ))}
              </div>
            </section>
          </>
        ) : (
          <div className="wrap inside">
            {!session ? (
              <p role="status">
                {error
                  ? '서버 연결을 확인해 주세요.'
                  : '이용 정보를 불러오는 중입니다.'}
              </p>
            ) : result && (route === 'result' || route === 'booking') ? (
              <PaymentReturn
                session={session}
                result={result}
                finish={() => {
                  sessionStorage.removeItem('payment-return')
                  setResult(null)
                  location.hash = 'mine'
                }}
              />
            ) : route === 'mine' ? (
              <MyBookings session={session} />
            ) : route === 'login' ? (
              session.userId ? (
                <MyBookings session={session} />
              ) : (
                <Login session={session} />
              )
            ) : (
              <ReservationForm session={session} />
            )}
          </div>
        )}
      </main>
      <footer>
        <div className="wrap">
          <strong>설매 SEOLMAE</strong>
          <p>미디어아트 전시 · 눈썰매장</p>
          <a href="#visit">방문 안내 →</a>
        </div>
      </footer>
    </>
  )
}
createRoot(document.getElementById('root')).render(<App />)
