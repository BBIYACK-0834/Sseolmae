export async function request(
  path,
  { method = 'GET', body, csrf, signal } = {},
) {
  let response
  try {
    response = await fetch('/api' + path, {
      method,
      signal,
      credentials: 'same-origin',
      headers: {
        ...(body ? { 'Content-Type': body instanceof URLSearchParams ? 'application/x-www-form-urlencoded' : 'application/json' } : {}),
        ...(csrf ? { 'X-CSRF-TOKEN': csrf } : {}),
      },
      body: body instanceof URLSearchParams ? body : body ? JSON.stringify(body) : undefined,
    })
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new Error('서버 연결을 확인한 뒤 다시 시도해 주세요.')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    if (path === '/auth/login' && response.status === 401) throw new Error('이메일 또는 비밀번호를 확인해 주세요.')
    if ([401, 403].includes(response.status))
      throw new Error('로그인 상태를 확인하려면 페이지를 새로고침해 주세요.')
    const error = new Error(data?.detail ?? '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    error.status = response.status
    throw error
  }
  return data
}
