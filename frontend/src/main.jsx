import { StrictMode, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { loadExperiences } from './api'
import ReservationForm from './ReservationForm'
import './styles.css'

const experiences = [
  {
    id: 'media-art',
    theme: 'light',
    title: '미디어아트',
    description: '빛과 영상, 소리가 만드는 실내 전시.',
    details: '전시 일정, 관람 동선과 작품별 촬영 안내는 개장 전에 공개합니다.',
  },
  {
    id: 'snow-sled',
    theme: 'snow',
    title: '눈썰매장',
    description: '눈 위를 달리는 겨울 야외 체험.',
    details: '눈썰매장 운영 일정과 연령·신장 등 탑승 기준은 개장 전에 공개합니다.',
  },
]

const visitInfo = [
  ['운영시간', '전시 마지막 입장 시간과 눈썰매 운영 회차는 추후 안내합니다.'],
  ['이용 요금', '개별 이용권, 통합권과 단체 요금은 추후 안내합니다.'],
  ['오시는 길', '시설 주소, 교통편과 주차 정보는 추후 안내합니다.'],
]

function Contours({ theme }) {
  return (
    <div className={`contours ${theme}`} aria-hidden="true">
      {Array.from({ length: 13 }, (_, index) => <i key={index} style={{ '--ring': index }} />)}
    </div>
  )
}

function App() {
  const [catalog, setCatalog] = useState([])

  useEffect(() => {
    const controller = new AbortController()
    loadExperiences(controller.signal).then(setCatalog).catch(() => {
      // Static descriptions keep the public landing page readable during an API interruption.
    })
    return () => controller.abort()
  }, [])

  return (
    <>
      <a className="skip-link" href="#main">본문 바로가기</a>
      <header className="header">
        <div className="content header-inner">
          <a className="brand" href="#main">미디어아트 · 눈썰매</a>
          <nav aria-label="주 메뉴">
            <a href="#media-art">미디어아트</a>
            <a href="#snow-sled">눈썰매장</a>
            <a href="#visit">이용 안내</a>
            <a href="#booking">예매</a>
          </nav>
        </div>
      </header>
      <main id="main">
        <section className="hero" aria-labelledby="main-title">
          <Contours theme="light" />
          <div className="content hero-content">
            <p className="hero-label">미디어아트 전시와 눈썰매장</p>
            <h1 id="main-title">빛 속을 걷고,<br />눈 위를 달리다.</h1>
            <p className="hero-description">실내에서 미디어아트를 감상하고<br />야외에서 눈썰매를 즐겨보세요.</p>
            <div className="actions">
              <a className="button" href="#booking">이용권 예매 <span aria-hidden="true">→</span></a>
              <a className="button secondary" href="#visit">이용 안내 <span aria-hidden="true">→</span></a>
            </div>
          </div>
        </section>
        <section className="content section" aria-labelledby="experience-heading">
          <h2 id="experience-heading">두 가지 즐길 거리</h2>
          <div className="experience-grid">
            {experiences.map((experience) => {
              const item = catalog.find(({ id }) => id === experience.id) ?? experience
              return (
                <article className="experience" key={experience.id} id={experience.id}>
                  <Contours theme={experience.theme} />
                  <div className="card-body">
                    <h3>{item.title}</h3>
                    <p>{item.description}</p>
                    <details>
                      <summary>자세히 보기</summary>
                      <p>{experience.details}</p>
                    </details>
                  </div>
                </article>
              )
            })}
          </div>
        </section>
        <section className="content section" id="visit" aria-labelledby="visit-heading">
          <h2 id="visit-heading">방문 전 확인</h2>
          <div className="visit-grid">
            {visitInfo.map(([title, description]) => (
              <article className="card-body info" key={title}>
                <h3>{title}</h3>
                <p>{description}</p>
              </article>
            ))}
          </div>
        </section>
        <section className="content section booking" id="booking" aria-labelledby="booking-heading">
          <div>
            <h2 id="booking-heading">이용권 예매</h2>
            <p>방문 날짜와 이용권을 선택해 테스트로 접수할 수 있습니다.</p>
            <p className="muted">정식 예매 시작일은 추후 안내합니다.</p>
          </div>
          <ReservationForm />
        </section>
      </main>
      <footer>
        <div className="content footer-inner">
          <a className="brand" href="#main">미디어아트 · 눈썰매</a>
          <p>주소와 연락처는 추후 안내합니다.</p>
        </div>
      </footer>
    </>
  )
}

createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>)
