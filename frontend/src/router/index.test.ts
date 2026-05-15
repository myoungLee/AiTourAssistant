/*
 * @author myoung
 */
import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'

import { ACCESS_TOKEN_KEY } from '@/api/http'

import { createAppRouter } from './index'

describe('app router', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('redirects anonymous users to login and preserves the original target', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/planner')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/planner')
  })

  it('allows authenticated users to open protected pages', async () => {
    window.localStorage.setItem(ACCESS_TOKEN_KEY, 'token-123')
    const router = createAppRouter(createMemoryHistory())

    await router.push('/planner')

    expect(router.currentRoute.value.name).toBe('trip-planner')
  })
})
