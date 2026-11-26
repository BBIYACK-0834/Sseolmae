import { useState } from 'react'
import { submitReservation } from './api'

const ticketLabels = {
  MEDIA_ART: '미디어아트',
  SNOW_SLED: '눈썰매',
  COMBO: '미디어아트 + 눈썰매',
}

const initialValues = { visitDate: '', ticketType: '', adults: '1', children: '0' }

function venueToday() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())
}

export default function ReservationForm() {
  const [values, setValues] = useState(initialValues)
  const [receipt, setReceipt] = useState(null)
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  function update(event) {
    const { name, value } = event.target
    setValues((previous) => ({ ...previous, [name]: value }))
    setError('')
  }

  async function submit(event) {
    event.preventDefault()
    if (pending) return

    const adults = Number(values.adults)
    const children = Number(values.children)
    if (adults + children < 1 || adults + children > 20) {
      setError('전체 인원은 1명 이상 20명 이하여야 합니다.')
      return
    }

    setError('')
    setPending(true)
    try {
      setReceipt(await submitReservation({ ...values, adults, children }))
    } catch (failure) {
      setError(failure.message)
    } finally {
      setPending(false)
    }
  }

  if (receipt) {
    return (
      <div className="form-box" role="status" aria-label="예매 접수 결과">
        <h3>테스트 예매 접수 완료</h3>
        <p>{receipt.visitDate} · {ticketLabels[receipt.ticketType]} · 총 {receipt.adults + receipt.children}명</p>
        <p className="muted">접수 번호 <code>{receipt.id}</code></p>
        <button className="button secondary" type="button" onClick={() => setReceipt(null)}>다시 작성</button>
      </div>
    )
  }

  return (
    <form className="form-box" onSubmit={submit}>
      <fieldset disabled={pending}>
        <legend>방문 정보</legend>
        <div className="field-grid">
          <div className="field">
            <label htmlFor="visitDate">방문 날짜</label>
            <input id="visitDate" type="date" name="visitDate" required min={venueToday()} value={values.visitDate} onChange={update} />
          </div>
          <div className="field">
            <label htmlFor="ticketType">이용권</label>
            <select id="ticketType" name="ticketType" required value={values.ticketType} onChange={update}>
              <option value="">이용권 선택</option>
              {Object.entries(ticketLabels).map(([value, title]) => <option key={value} value={value}>{title}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="adults">성인 인원</label>
            <input id="adults" type="number" name="adults" min="0" max="20" step="1" required value={values.adults} onChange={update} />
          </div>
          <div className="field">
            <label htmlFor="children">어린이 인원</label>
            <input id="children" type="number" name="children" min="0" max="20" step="1" required value={values.children} onChange={update} />
          </div>
        </div>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="button" type="submit">{pending ? '요청 중…' : '테스트 예매 접수'}</button>
      </fieldset>
    </form>
  )
}
