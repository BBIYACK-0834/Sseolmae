import { useState } from 'react'

const seoul = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' })
export const visitDay = value => seoul.format(new Date(value))

export default function VisitCalendar({ days, selected, onSelect }) {
  const today = visitDay(Date.now())
  const start = today.slice(0, 7)
  const end = (days.at(-1) || today).slice(0, 7)
  const [view, setView] = useState(null)
  const month = view && view >= start && view <= end ? view : (days[0] || today).slice(0, 7)
  const [year, m] = month.split('-').map(Number)
  const offset = new Date(Date.UTC(year, m - 1, 1)).getUTCDay()
  const length = new Date(Date.UTC(year, m, 0)).getUTCDate()
  function move(delta) {
    setView(new Date(Date.UTC(year, m - 1 + delta, 1)).toISOString().slice(0, 7))
  }
  return <section className="visit-cal" aria-label="방문일 선택">
    <h3>방문일 선택</h3>
    <div className="month-nav">
      <button type="button" aria-label="이전 달" disabled={month <= start} onClick={() => move(-1)}>‹</button>
      <strong aria-live="polite">{year}년 {m}월</strong>
      <button type="button" aria-label="다음 달" disabled={month >= end} onClick={() => move(1)}>›</button>
    </div>
    <div className="day-grid">
      {Array.from('일월화수목금토', d => <small key={d}>{d}</small>)}
      {Array.from({length: offset}, (_, i) => <span key={'pad'+i} />)}
      {Array.from({length}, (_, i) => {
        const key = month + '-' + String(i+1).padStart(2,'0')
        return <button type="button" key={key} disabled={!days.includes(key)} aria-current={key === today ? 'date' : undefined} aria-label={key} aria-pressed={selected === key} onClick={() => onSelect(key)}>{i+1}</button>
      })}
    </div>
  </section>
}
