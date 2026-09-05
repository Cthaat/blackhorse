import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, getCurrentInstance, reactive, ref, watch } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
// The bundled ESM build includes the real validator and matches browser behavior
// without Node's nested default export for the async-validator CommonJS package.
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus/dist/index.full.mjs'
import Login from '@/views/login.vue'

const { getCodeImg, login, push } = vi.hoisted(() => ({
  getCodeImg: vi.fn(),
  login: vi.fn(),
  push: vi.fn()
}))

vi.mock('@/api/login', () => ({ getCodeImg }))
vi.mock('@/store/modules/user', () => ({ default: () => ({ login }) }))

const wrappers = []

function render() {
  const wrapper = mount(Login, {
    global: {
      components: { ElButton, ElForm, ElFormItem, ElInput },
      stubs: { SvgIcon: true }
    }
  })
  wrappers.push(wrapper)
  return wrapper
}

async function enterCredentials(wrapper, code = '1234') {
  await wrapper.get('input[name="username"]').setValue('researcher')
  await wrapper.get('input[name="password"]').setValue('test-password')
  if (wrapper.find('input[name="code"]').exists()) {
    await wrapper.get('input[name="code"]').setValue(code)
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  getCodeImg.mockReset().mockResolvedValue({ code: 200, img: 'captcha-image', uuid: 'captcha-1' })
  login.mockReset().mockResolvedValue(undefined)
  push.mockResolvedValue(undefined)
  const route = reactive({ query: { redirect: '/lab/equipment', status: 'active' } })
  for (const [name, value] of Object.entries({ computed, getCurrentInstance, ref, watch })) {
    vi.stubGlobal(name, value)
  }
  vi.stubGlobal('useRoute', () => route)
  vi.stubGlobal('useRouter', () => ({ push }))
})

afterEach(() => {
  wrappers.forEach(wrapper => wrapper.unmount())
  wrappers.length = 0
  vi.unstubAllGlobals()
})

describe('login captcha and form contract', () => {
  it('shows a recoverable captcha error and retries without losing credentials', async () => {
    getCodeImg.mockRejectedValueOnce(new Error('Network unavailable'))
    const wrapper = render()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('验证码加载失败')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await enterCredentials(wrapper)
    await wrapper.get('.captcha-refresh').trigger('click')
    await flushPromises()

    expect(getCodeImg).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('.captcha-refresh img').attributes('src')).toBe('data:image/gif;base64,captcha-image')
    expect(wrapper.get('input[name="password"]').element.value).toBe('test-password')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('blocks login and repeated refresh while the captcha request is pending', async () => {
    let resolveCaptcha
    getCodeImg.mockImplementationOnce(() => new Promise(resolve => { resolveCaptcha = resolve }))
    const wrapper = render()

    expect(wrapper.get('.captcha-refresh').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.captcha-refresh').attributes('aria-busy')).toBe('true')
    await enterCredentials(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(login).not.toHaveBeenCalled()
    expect(getCodeImg).toHaveBeenCalledTimes(1)

    resolveCaptcha({ code: 200, captchaEnabled: true, img: 'new-image', uuid: 'captcha-2' })
    await flushPromises()
    expect(wrapper.get('.captcha-refresh').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('.captcha-refresh img').attributes('src')).toBe('data:image/gif;base64,new-image')
  })

  it('submits the same login payload and keeps redirect query parameters', async () => {
    const wrapper = render()
    await flushPromises()
    await enterCredentials(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(login).toHaveBeenCalledWith({
      username: 'researcher', password: 'test-password', code: '1234', uuid: 'captcha-1'
    })
    expect(push).toHaveBeenCalledWith({ path: '/lab/equipment', query: { status: 'active' } })
  })

  it('honors the server captcha-disabled response', async () => {
    getCodeImg.mockResolvedValueOnce({ code: 200, captchaEnabled: false })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.find('input[name="code"]').exists()).toBe(false)
    await enterCredentials(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(login).toHaveBeenCalledWith({
      username: 'researcher', password: 'test-password', code: '', uuid: ''
    })
  })

  it('keeps required-field validation before attempting login', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(login).not.toHaveBeenCalled()
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(3)
  })

  it('submits once when the form receives repeated submit events during validation', async () => {
    const wrapper = render()
    await flushPromises()
    await enterCredentials(wrapper)
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(login).toHaveBeenCalledTimes(1)
  })

  it('refreshes the captcha after a rejected login and clears the old answer', async () => {
    login.mockRejectedValueOnce(new Error('Invalid credentials'))
    getCodeImg.mockResolvedValueOnce({ code: 200, img: 'first-image', uuid: 'captcha-1' })
      .mockResolvedValueOnce({ code: 200, img: 'second-image', uuid: 'captcha-2' })
    const wrapper = render()
    await flushPromises()
    await enterCredentials(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(getCodeImg).toHaveBeenCalledTimes(2)
    expect(wrapper.get('input[name="code"]').element.value).toBe('')
    expect(wrapper.get('.captcha-refresh img').attributes('src')).toBe('data:image/gif;base64,second-image')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    expect(push).not.toHaveBeenCalled()
  })

  it('labels form controls and supports password managers without remembering credentials', async () => {
    const wrapper = render()
    await flushPromises()

    for (const name of ['username', 'password', 'code']) {
      const input = wrapper.get(`input[name="${name}"]`)
      expect(wrapper.find(`label[for="${input.attributes('id')}"]`).exists()).toBe(true)
      expect(input.element.value).toBe('')
    }
    expect(wrapper.get('input[name="username"]').attributes('autocomplete')).toBe('username')
    expect(wrapper.get('input[name="password"]').attributes('autocomplete')).toBe('current-password')
    expect(wrapper.get('.captcha-refresh').attributes('type')).toBe('button')
    expect(wrapper.get('.captcha-refresh').attributes('aria-label')).toBe('刷新验证码')
    await wrapper.get('input[name="password"]').setValue('test-password')
    await wrapper.get('button[aria-label="显示密码"]').trigger('click')
    expect(wrapper.get('input[name="password"]').attributes('type')).toBe('text')
    expect(wrapper.get('button[aria-label="隐藏密码"]').attributes('aria-pressed')).toBe('true')
    await wrapper.get('button[aria-label="隐藏密码"]').trigger('click')
    expect(wrapper.get('input[name="password"]').attributes('type')).toBe('password')
  })
})
