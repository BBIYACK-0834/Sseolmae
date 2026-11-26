export async function request(path, options) {
  const response = await fetch(`/api${path}`, options)
  const body = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(body?.detail ?? '요청에 실패했습니다. 잠시 후 다시 시도해 주세요.')
  }
  return body
}

export async function loadExperiences(signal) {
  return request('/experiences', { signal })
}

export async function submitReservation(reservation) {
  return request('/reservations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(reservation),
  })
}
