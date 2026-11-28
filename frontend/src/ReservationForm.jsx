import VisitCalendar, { visitDay } from './VisitCalendar'
import Login from './Login'
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
  if (booking.status !== 'PENDING' || Date.parse(booking.expiresAt) <= Date.now())
    throw new Error('결제 가능한 시간이 지났거나 처리 중인 예약입니다. 내 예약을 확인해 주세요.')
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
            : '/payments/' + row.id + (action === 'refund' ? '/refund' : '/retry'),
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
          <b>{r.admittedAt ? '입장 완료' : r.refundRequested ? (r.status==='CANCELLED'?'환불 완료':'환불 확인 중') : states[r.status]}</b>
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
            {r.status === 'CONFIRMED' && !r.admittedAt && (r.refundRequested || Date.parse(r.startsAt)>Date.now()) && (
              <button disabled={busy} onClick={() => {if(r.refundRequested || window.confirm('예약 전체를 취소하고 전액 환불할까요?')) act(r,'refund')}}>{r.refundRequested?'환불 다시 확인':'전액 환불'}</button>
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
  const [canRetry, setCanRetry] = useState(false)
  useEffect(() => {
    if (!session.userId) return
    if (!result.failed && (!result.approval?.paymentKey || !result.approval.orderId || !Number.isSafeInteger(result.approval.amount) || result.approval.amount < 1)) {
      setMessage('결제 복귀 정보가 누락되었습니다. 내 예약에서 상태를 확인해 주세요.')
      return
    }
    if (result.failed) {
      setMessage(
        '결제창에서 돌아왔습니다. 내 예약에서 현재 상태와 결제 가능 시간을 확인해 주세요.',
      )
      return
    }
    let active = true
    setCanRetry(false)
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
        if (active) {
          setMessage(e.message)
          setCanRetry(!e.status || e.status >= 500 || e.status === 429)
        }
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
        {!ready && canRetry && (
          <button onClick={() => setRetry((v) => v + 1)}>승인 다시 확인</button>
        )}
        <button className="quiet" onClick={finish}>
          내 예약 보기
        </button>
      </div>
    </section>
  )
}

export default function ReservationForm({ session, initialType }) {
  const [slots, setSlots] = useState([])
  const [type, setType] = useState(initialType)
  const [day, setDay] = useState('')
  const [slotId, setSlotId] = useState('')
  const [adults, setAdults] = useState(0)
  const [children, setChildren] = useState(0)
  const [loaded, setLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const details = useRef(null)
  const drag = useRef(null)
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
  const upcoming = slots.filter(s => new Date(s.startsAt).getTime() > Date.now())
  const days = [...new Set(upcoming.filter(s => s.remaining > 0).map(s => visitDay(s.startsAt)))].sort()
  const selected = days.includes(day) ? day : ''
  const options = upcoming.filter(s => s.ticketType === type && selected === visitDay(s.startsAt))
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
      if (['EXPIRED','CANCELLED','CONFIRMED'].includes(booking.status)) sessionStorage.removeItem(storageKey)
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
        <p>달력에서 방문일을 고른 뒤 이용권과 회차를 선택해 주세요.</p>
        <fieldset disabled={busy}>
          <VisitCalendar days={days} selected={selected} onSelect={value => { setDay(value); setSlotId('') }} />
          {loaded && !days.length && <p role="status">현재 예매 가능한 날짜가 없습니다.</p>}
          <section className="booking-details" ref={details}>
            <button type="button" className="sheet-grip" aria-label="인원 및 회차 입력 영역으로 이동"
              onPointerDown={e => { drag.current = { y: e.clientY, top: window.scrollY }; e.currentTarget.setPointerCapture(e.pointerId) }}
              onPointerMove={e => { if (drag.current) window.scrollTo(0, drag.current.top + drag.current.y - e.clientY) }}
              onPointerUp={e => { const moved = drag.current && Math.abs(e.clientY - drag.current.y) > 6; drag.current = null; if (!moved) details.current.scrollIntoView({block:'start'}) }}
              onPointerCancel={() => { drag.current = null }}
              onClick={e => { if (e.detail === 0) details.current.scrollIntoView({block:'start'}) }}>
              <span aria-hidden="true" />
            </button>
            <h3>인원 선택</h3>
            {[['성인', adults, setAdults], ['어린이', children, setChildren]].map(([name, value, change]) =>
              <div className="guest-row" key={name}><strong>{name}</strong><div className="stepper">
                <button type="button" aria-label={name+' 한 명 줄이기'} disabled={!value} onClick={() => change(value-1)}>−</button>
                <output aria-label={name+' 인원'}>{value}</output>
                <button type="button" aria-label={name+' 한 명 추가'} disabled={count >= Math.min(20, slot?.remaining ?? 20)} onClick={() => change(value+1)}>+</button>
              </div></div>)}
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
          <section className="rounds" aria-label="방문 회차">
            <h3>방문 회차</h3>
            {options.length ? <div className="round-grid">{options.map(s =>
              <button type="button" key={s.id} disabled={s.remaining < 1}
                aria-pressed={slotId === s.id} onClick={() => setSlotId(s.id)}>
                <strong>{new Intl.DateTimeFormat('ko-KR', {timeZone:'Asia/Seoul',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date(s.startsAt))}</strong>
                <small>{s.remaining ? '잔여 ' + s.remaining + '석' : '매진'}</small>
              </button>
            )}</div> : <p role="status">{!loaded ? '회차 확인 중' : selected ? '이 이용권에 등록된 회차가 없습니다.' : '달력에서 방문일을 선택해 주세요.'}</p>}
          </section>
          {error && (
            <p role="alert" className="notice">
              {error} <a href="#mine">내 예약 보기 →</a>
            </p>
          )}
          {slot && <p>성인 {won(slot.price)} · 어린이 {won(slot.childPrice)} / 1인</p>}
          <div className="booking-total"><span>총 {count}명</span><strong>{slot ? won(slot.price * adults + slot.childPrice * children) : '회차 선택 후 금액 표시'}</strong></div>
          {session.userId && !session.clientKey && <p className="notice">결제 서비스 준비 중입니다.</p>}
          {session.userId ? (
            <button disabled={!count || !slot || slot.remaining < count || !session.clientKey || busy}>
              {busy ? '예약 처리 중…' : '예약하고 결제하기'}
            </button>
          ) : (
            <a className="button" href="#login">
              로그인하고 예매하기
            </a>
          )}
          </section>
        </fieldset>
      </form>

    </section>
  )
}
