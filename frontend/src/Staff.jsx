import { useEffect, useState } from 'react'
import { request } from './api'

const types = {MEDIA_ART:'미디어아트',SNOW_SLED:'눈썰매장',COMBO:'통합권'}
const empty = {capacity:30,price:12000,childPrice:12000,saleOpen:true}

export default function Staff({session}) {
  const [rows,setRows]=useState([]), [edit,setEdit]=useState(''), [terms,setTerms]=useState(empty)
  const [type,setType]=useState('MEDIA_ART'), [time,setTime]=useState('')
  const [number,setNumber]=useState(''), [booking,setBooking]=useState(null)
  const [busy,setBusy]=useState(false), [message,setMessage]=useState('')
  async function refresh() {
    const data=await request('/staff/slots')
    setRows(data.map(row=>Object.fromEntries(Object.entries(row).map(([k,v])=>[k.toLowerCase(),v]))))
  }
  useEffect(()=>{refresh().catch(e=>setMessage(e.message))},[])
  async function run(fn){
    setBusy(true);setMessage('')
    try {await fn()} catch(e){setMessage(e.message)} finally{setBusy(false)}
  }
  function select(row){
    setEdit(row?.id || '')
    setTerms(row?{capacity:row.capacity,price:row.price,childPrice:row.child_price,saleOpen:row.sale_open}:empty)
  }
  return <section className="panel"><h2>운영 관리</h2>
    <p role="status">{message}</p><fieldset disabled={busy}>
    <h3>판매 회차</h3>
    <select aria-label="관리할 회차" value={edit} onChange={e=>select(rows.find(r=>r.id===e.target.value))}>
      <option value="">새 회차 등록</option>{rows.map(r=><option key={r.id} value={r.id}>{new Date(r.starts_at).toLocaleString('ko-KR',{timeZone:'Asia/Seoul'})} · {types[r.ticket_type]} · {r.sale_open?'판매 중':'판매 중지'}</option>)}
    </select>
    <form onSubmit={e=>{e.preventDefault();run(async()=>{
      await request('/staff/slots'+(edit?'/'+edit:''),{method:edit?'PUT':'POST',csrf:session.csrf,body:edit?terms:{terms,ticketType:type,startsAt:time+':00+09:00'}})
      await refresh();select(null);setTime('');setMessage('회차를 저장했습니다.')
    })}}>
      {!edit && <><label>이용권<select value={type} onChange={e=>setType(e.target.value)}>{Object.entries(types).map(([k,v])=><option key={k} value={k}>{v}</option>)}</select></label>
      <label>시작 시각 (한국 시간)<input required type="datetime-local" value={time} onChange={e=>setTime(e.target.value)}/></label></>}
      {Object.entries({capacity:'정원',price:'성인 요금 (원)',childPrice:'어린이 요금 (원)'}).map(([k,v])=><label key={k}>{v}<input required type="number" min="1" value={terms[k]} onChange={e=>setTerms({...terms,[k]:Number(e.target.value)})}/></label>)}
      <label>판매 상태<select value={String(terms.saleOpen)} onChange={e=>setTerms({...terms,saleOpen:e.target.value==='true'})}><option value="true">판매 중</option><option value="false">판매 중지</option></select></label>
      <button>회차 저장</button></form>
    <h3>현장 입장</h3><form onSubmit={e=>{e.preventDefault();run(async()=>{setBooking(null);setBooking(await request('/staff/reservations/'+encodeURIComponent(number.trim())))})}}>
    <label>예약 번호<input required value={number} onChange={e=>{setNumber(e.target.value);setBooking(null)}}/></label><button>예약 조회</button></form>
    {booking && <article><p>{types[booking.ticketType]} · 성인 {booking.adults}명 · 어린이 {booking.children}명</p>
    <p>{new Date(booking.startsAt).toLocaleString('ko-KR',{timeZone:'Asia/Seoul'})} · {booking.status}</p>
    <button disabled={!!booking.admittedAt || booking.refundRequested || booking.status!=='CONFIRMED'} onClick={()=>run(async()=>{setBooking(await request('/staff/reservations/'+booking.id+'/admit',{method:'POST',csrf:session.csrf}));setMessage('입장 처리했습니다.')})}>{booking.admittedAt?'입장 완료':'일행 전체 입장 처리'}</button></article>}
    </fieldset></section>
}
