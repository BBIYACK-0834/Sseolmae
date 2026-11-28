import { useEffect, useRef, useState } from 'react'
import { loadTossPayments } from '@tosspayments/tosspayments-sdk'
import { request } from './api'

const labels = {
  MEDIA_ART: '미디어아트 전시',
  SNOW_SLED: '눈썰매장',
  COMBO: '전시 + 눈썰매 통합권',
}
const states = {
  PENDING: '결제 대기',
  PROCESSING: '승인 확인 중',
  CONFIRMED: '예약 확정',
  CANCELLED: '취소',
  EXPIRED: '결제 기한 만료',
}
const won = (value) => new Intl.NumberFormat('ko-KR').format(value) + '원'
const date = (value) =>
  new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))

async function checkout(session, booking) {
  if (!session.clientKey) throw new Error('결제 판매 개시 전입니다.')
  const toss = await loadTossPayments(session.clientKey)
  await toss.payment({ customerKey: session.userId }).requestPayment({
    method: 'CARD',
    amount: { currency: 'KRW', value: booking.amount },
    orderId: booking.id,
    orderName: labels[booking.ticketType] + ' 이용권',
    successUrl: location.origin + '/payment/success',
    failUrl: location.origin + '/payment/fail',
  })
}

export function Login({ session }) {
  return (
    <section className="panel login">
      <h2>로그인</h2>
      <p>예약과 결제 내역을 확인하려면 카카오 계정으로 로그인하세요.</p>
      {session.kakaoReady ? (
        <a className="button kakao" href="/oauth2/authorization/kakao">
          카카오로 계속하기
        </a>
      ) : (
        <p className="notice">카카오 로그인 연동 준비 중입니다.</p>
      )}
      <a className="text-link" href="#home">
        홈으로 →
      </a>
    </section>
  )
}

export function MyBookings({ session }) {
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [version, setVersion] = useState(0)
  useEffect(() => {
    if (!session.userId) return
    const controller = new AbortController()
    request('/reservations', { signal: controller.signal })
      .then(setRows)
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message)
      })
    return () => controller.abort()
  }, [session.userId, version])
  async function act(row, action) {
    setBusy(true)
    setError('')
    try {
      if (action === 'pay') await checkout(session, row)
      else {
        await request(
          action === 'cancel'
            ? '/reservations/' + row.id
            : '/payments/' + row.id + '/retry',
          {
            method: action === 'cancel' ? 'DELETE' : 'POST',
            csrf: session.csrf,
          },
        )
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
      setVersion((v) => v + 1)
    }
  }
  if (!session.userId) return <Login session={session} />
  return (
    <section className="panel">
      <h2>내 예약</h2>
      {error && (
        <p role="alert" className="notice">
          {error}
        </p>
      )}
      {!rows && !error && <p>예약을 불러오는 중입니다.</p>}
      {rows?.length === 0 && (
        <p>
          아직 예약 내역이 없습니다. <a href="#booking">이용권 예매 →</a>
        </p>
      )}
      {rows?.map((r) => (
        <article className="order" key={r.id}>
          <strong>{labels[r.ticketType]}</strong>
          <b>{states[r.status]}</b>
          <p>
            {date(r.startsAt)} · {r.adults + r.children}명 · {won(r.amount)}
          </p>
          <small>예약 번호 {r.id}</small>
          <div className="actions">
            {r.status === 'PENDING' && (
              <>
                <button
                  disabled={busy || !session.clientKey}
                  onClick={() => act(r, 'pay')}
                >
                  결제하기
                </button>
                <button
                  className="quiet"
                  disabled={busy}
                  onClick={() => act(r, 'cancel')}
                >
                  예약 취소
                </button>
              </>
            )}
            {r.status === 'PROCESSING' && (
              <button disabled={busy} onClick={() => act(r, 'retry')}>
                승인 다시 확인
              </button>
            )}
          </div>
        </article>
      ))}
    </section>
  )
}

export function PaymentReturn({ session, result, finish }) {
  const [message, setMessage] = useState(
    '결제사 승인 결과를 확인하고 있습니다.',
  )
  const [ready, setReady] = useState(false)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    if (!session.userId) return
    if (result.failed) {
      setMessage(
        '결제가 완료되지 않았습니다. 내 예약에서 결제를 다시 진행할 수 있습니다.',
      )
      return
    }
    let active = true
    request('/payments/confirm', {
      method: 'POST',
      csrf: session.csrf,
      body: result.approval,
    })
      .then((r) => {
        if (active) {
          setReady(true)
          setMessage(
            labels[r.ticketType] +
              ' · ' +
              date(r.startsAt) +
              ' · ' +
              won(r.amount),
          )
        }
      })
      .catch((e) => {
        if (active) setMessage(e.message)
      })
    return () => {
      active = false
    }
  }, [session, result, retry])
  if (!session.userId) return <Login session={session} />
  return (
    <section className="panel">
      <h2>{ready ? '예약이 확정되었습니다' : '결제 결과'}</h2>
      <p role="status">{message}</p>
      <div className="actions">
        {!ready && !result.failed && (
          <button onClick={() => setRetry((v) => v + 1)}>승인 다시 확인</button>
        )}
        <button className="quiet" onClick={finish}>
          내 예약 보기
        </button>
      </div>
    </section>
  )
}

export default function ReservationForm({ session }) {
  const [slots, setSlots] = useState([])
  const [type, setType] = useState('MEDIA_ART')
  const [slotId, setSlotId] = useState('')
  const [adults, setAdults] = useState('1')
  const [children, setChildren] = useState('0')
  const [loaded, setLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submitting = useRef(false)
  const [refresh, setRefresh] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    request('/slots', { signal: controller.signal })
      .then((list) => {
        setSlots(list)
        setLoaded(true)
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message)
      })
    return () => controller.abort()
  }, [refresh])
  const options = slots.filter((s) => s.ticketType === type)
  const slot = options.find((s) => s.id === slotId)
  const count = Number(adults) + Number(children)
  async function submit(event) {
    event.preventDefault()
    if (!slot || submitting.current) return
    if (count < 1 || count > 20 || count > slot.remaining) {
      setError('인원과 회차 잔여석을 확인해 주세요.')
      return
    }
    submitting.current = true
    setBusy(true)
    setError('')
    const storageKey = ['hold', session.userId, slot.id, adults, children].join(
      ':',
    )
    let held = false
    try {
      const requestId =
        sessionStorage.getItem(storageKey) || crypto.randomUUID()
      sessionStorage.setItem(storageKey, requestId)
      const booking = await request('/reservations', {
        method: 'POST',
        csrf: session.csrf,
        body: {
          requestId,
          slotId: slot.id,
          adults: Number(adults),
          children: Number(children),
        },
      })
      held = true
      sessionStorage.removeItem(storageKey)
      await checkout(session, booking)
    } catch (e) {
      setError(
        held
          ? '예약이 접수되었습니다. 내 예약에서 다시 결제할 수 있습니다. ' +
              e.message
          : e.message,
      )
    } finally {
      submitting.current = false
      setBusy(false)
      setRefresh((v) => v + 1)
    }
  }
  return (
    <section className="booking-layout">
      <form className="panel" onSubmit={submit}>
        <h2>이용권 예매</h2>
        <p>이용권과 방문 회차, 인원을 선택해 주세요.</p>
        <fieldset disabled={busy}>
          <label>
            이용권
            <select
              value={type}
              onChange={(e) => {
                setType(e.target.value)
                setSlotId('')
              }}
            >
              {Object.entries(labels).map(([key, name]) => (
                <option key={key} value={key}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <label>
            방문 회차
            <select
              required
              value={slotId}
              onChange={(e) => setSlotId(e.target.value)}
            >
              <option value="">
                {!loaded
                  ? '회차 확인 중'
                  : !options.length
                    ? '등록된 회차가 없습니다'
                    : '회차를 선택하세요'}
              </option>
              {options.map((s) => (
                <option key={s.id} disabled={s.remaining < 1} value={s.id}>
                  {date(s.startsAt)} · 잔여 {s.remaining}석
                </option>
              ))}
            </select>
          </label>
          <div className="pair">
            <label>
              성인
              <input
                type="number"
                min="0"
                max="20"
                required
                value={adults}
                onChange={(e) => setAdults(e.target.value)}
              />
            </label>
            <label>
              어린이
              <input
                type="number"
                min="0"
                max="20"
                required
                value={children}
                onChange={(e) => setChildren(e.target.value)}
              />
            </label>
          </div>
          {error && (
            <p role="alert" className="notice">
              {error} <a href="#mine">내 예약 보기 →</a>
            </p>
          )}
          {session.userId ? (
            <button disabled={!slot || !session.clientKey || busy}>
              {busy ? '예약 처리 중…' : '예약하고 결제하기'}
            </button>
          ) : (
            <a className="button" href="#login">
              로그인하고 예매하기
            </a>
          )}
        </fieldset>
      </form>
      <aside className="panel summary">
        <h3>예매 요약</h3>
        <p>{labels[type]}</p>
        <p>
          {slot ? date(slot.startsAt) : '방문 회차 선택 대기'} · {count}명
        </p>
        <hr />
        <strong>
          {slot ? won(slot.price * count) : '회차 선택 후 금액 표시'}
        </strong>
        <p>예약 후 10분 안에 결제를 완료해 주세요.</p>
        {session.userId && !session.clientKey && (
          <p className="notice">결제 서비스 준비 중입니다.</p>
        )}
      </aside>
    </section>
  )
}
