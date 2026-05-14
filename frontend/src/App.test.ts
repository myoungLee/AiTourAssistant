/*
 * @author myoung
 */
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'

import App from './App.vue'
import router from './router'

describe('App', () => {
  it('renders home page through router', async () => {
    router.push('/home')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router, ElementPlus]
      }
    })

    expect(wrapper.text()).toContain('AI 旅游助手')
    expect(wrapper.text()).toContain('基础工程搭建中')
  })
})
