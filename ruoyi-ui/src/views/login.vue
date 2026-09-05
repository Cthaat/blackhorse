<template>
  <main class="login">
    <section class="login-shell" aria-label="实验室工作台登录">
      <div class="login-intro">
        <div class="intro-brand">
          <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M11 4h10M13 4v10L5 27h22l-8-13V4M10 20h12" /></svg>
          <span>LAB WORKSPACE</span>
        </div>
        <div class="intro-copy">
          <p class="intro-eyebrow">实验室工作台</p>
          <p class="intro-title">安全有据，<br>管理有序。</p>
          <p class="intro-description">连接安全巡检、设备管理与预约协作，<br>让每一项日常工作清晰可循。</p>
        </div>
        <svg class="lab-illustration" viewBox="0 0 440 220" fill="none" aria-hidden="true">
          <path d="M20 190h400M45 196v12m350-12v12" stroke="#62788d" stroke-width="2" />
          <rect x="38" y="173" width="366" height="17" rx="3" fill="#263c51" stroke="#62788d" />
          <rect x="67" y="38" width="135" height="91" rx="7" fill="#20384b" stroke="#91a7b8" stroke-width="2" />
          <path d="M80 102l24-25 21 11 28-31 32 13" stroke="#5ee0c0" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
          <path d="M80 115h108M127 130v28m-24 5h62" stroke="#91a7b8" stroke-width="2" stroke-linecap="round" />
          <circle cx="185" cy="49" r="3" fill="#5ee0c0" />
          <path d="M264 62h33m-26 0v35l-27 59a10 10 0 0 0 9 14h55a10 10 0 0 0 9-14l-27-59V62" fill="#20384b" stroke="#91a7b8" stroke-width="2" stroke-linejoin="round" />
          <path d="M263 128h35l15 32a4 4 0 0 1-4 5h-57a4 4 0 0 1-3-5z" fill="#0f766e" />
          <circle cx="273" cy="140" r="3" fill="#8be9cf" /><circle cx="290" cy="153" r="2" fill="#8be9cf" />
          <path d="M339 102h38m-32 0v55a13 13 0 0 0 26 0v-55" stroke="#91a7b8" stroke-width="2" stroke-linecap="round" />
          <path d="M350 135h16v22a8 8 0 0 1-16 0z" fill="#0f766e" />
          <path d="M230 24v12m-6-6h12M381 53v12m-6-6h12" stroke="#5ee0c0" stroke-width="2" stroke-linecap="round" />
          <path d="M28 149h15m-8-8v16" stroke="#62788d" stroke-width="2" stroke-linecap="round" />
        </svg>
        <ul class="intro-capabilities" aria-label="工作台功能">
          <li>安全巡检</li><li>设备管理</li><li>预约协作</li>
        </ul>
      </div>

      <div class="login-panel">
        <header class="login-heading">
          <p class="login-eyebrow">登录工作台</p>
          <h1>{{ title }}</h1>
          <p class="login-description">欢迎回来，请使用您的账号登录。</p>
        </header>
        <el-form
          ref="loginRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
          label-position="top"
          hide-required-asterisk
          @submit.prevent="handleLogin"
        >
          <el-form-item label="账号" prop="username" for="login-username">
            <el-input
              id="login-username"
              v-model="loginForm.username"
              name="username"
              type="text"
              size="large"
              autocomplete="username"
              aria-required="true"
              placeholder="请输入账号"
            >
              <template #prefix><svg-icon icon-class="user" class="input-icon" aria-hidden="true" /></template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码" prop="password" for="login-password">
            <el-input
              id="login-password"
              v-model="loginForm.password"
              name="password"
              :type="passwordVisible ? 'text' : 'password'"
              size="large"
              autocomplete="current-password"
              aria-required="true"
              placeholder="请输入密码"
            >
              <template #prefix><svg-icon icon-class="password" class="input-icon" aria-hidden="true" /></template>
              <template #suffix>
                <button
                  class="password-toggle"
                  type="button"
                  :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                  :aria-pressed="passwordVisible"
                  @click="passwordVisible = !passwordVisible"
                >{{ passwordVisible ? '隐藏' : '显示' }}</button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item v-if="captchaEnabled" label="验证码" prop="code" for="login-code">
            <div class="captcha-field">
              <el-input
                id="login-code"
                v-model="loginForm.code"
                name="code"
                size="large"
                autocomplete="off"
                aria-required="true"
                aria-describedby="captcha-help"
                placeholder="请输入验证码"
              >
                <template #prefix><svg-icon icon-class="validCode" class="input-icon" aria-hidden="true" /></template>
              </el-input>
              <button
                class="captcha-refresh"
                type="button"
                :aria-label="captchaError ? '重新加载验证码' : '刷新验证码'"
                :aria-busy="captchaLoading"
                :disabled="captchaLoading || loading"
                @click="getCode"
              >
                <span v-if="captchaLoading">加载中…</span>
                <span v-else-if="captchaError">点击重试</span>
                <img v-else :src="codeUrl" alt="验证码" class="captcha-image">
              </button>
            </div>
            <p v-if="captchaError" id="captcha-help" class="captcha-help is-error" role="alert">验证码加载失败，请点击重试。</p>
            <p v-else id="captcha-help" class="captcha-help" role="status">{{ captchaLoading ? '正在获取验证码，请稍候。' : '看不清？点击图片换一张。' }}</p>
          </el-form-item>
          <el-button
            :loading="loading"
            :disabled="captchaUnavailable"
            class="login-submit"
            size="large"
            type="primary"
            native-type="submit"
          >{{ loading ? '正在登录…' : '登录工作台' }}</el-button>
        </el-form>
        <p class="login-help">账号或密码遇到问题，请联系系统管理员。</p>
      </div>
    </section>
    <footer class="login-footer">{{ footerContent }}</footer>
  </main>
</template>

<script setup>
import { getCodeImg } from '@/api/login'
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'

const title = '实验室安全与设备管理系统'
const footerContent = defaultSettings.footerContent
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loginForm = ref({ username: '', password: '', code: '', uuid: '' })
const loginRules = {
  username: [{ required: true, trigger: 'blur', message: '请输入您的账号' }],
  password: [{ required: true, trigger: 'blur', message: '请输入您的密码' }],
  code: [{ required: true, trigger: 'change', message: '请输入验证码' }]
}

const codeUrl = ref('')
const loading = ref(false)
const passwordVisible = ref(false)
const captchaEnabled = ref(true)
const captchaLoading = ref(false)
const captchaError = ref(false)
const captchaUnavailable = computed(() => captchaEnabled.value && (captchaLoading.value || captchaError.value))
const redirect = ref(undefined)

watch(route, (newRoute) => {
  redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  if (loading.value || captchaUnavailable.value) return
  proxy.$refs.loginRef.validate(valid => {
    if (valid && !loading.value && !captchaUnavailable.value) {
      loading.value = true
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== 'redirect') {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || '/', query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        if (captchaEnabled.value) getCode()
      })
    }
  })
}

async function getCode() {
  if (captchaLoading.value) return
  captchaLoading.value = true
  captchaError.value = false
  codeUrl.value = ''
  loginForm.value.code = ''
  loginForm.value.uuid = ''
  try {
    const res = await getCodeImg()
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = 'data:image/gif;base64,' + res.img
      loginForm.value.uuid = res.uuid
    }
  } catch {
    captchaError.value = true
  } finally {
    captchaLoading.value = false
  }
}

getCode()
</script>

<style lang="scss" scoped>
.login {
  --login-canvas: var(--lab-canvas, #f3f5f4);
  --login-surface: var(--lab-surface, #fff);
  --login-ink: var(--lab-ink, #172b3d);
  --login-muted: var(--lab-muted, #617180);
  --login-border: var(--lab-border, #dce3e5);
  --el-color-primary: #0f766e;
  --el-color-primary-light-3: #348f87;
  --el-color-primary-light-5: #65aca5;
  --el-color-primary-light-7: #a2cec9;
  --el-color-primary-light-9: #e7f3f1;
  --el-color-primary-dark-2: #0b5e58;
  --el-color-danger: #b42318;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  min-height: 100dvh;
  padding: 3rem 2rem 1.5rem;
  color: var(--login-ink);
  background: var(--login-canvas);
}

.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  width: 100%;
  max-width: 1120px;
  overflow: hidden;
  border: 1px solid var(--login-border);
  border-radius: 1rem;
  background: var(--login-surface);
  box-shadow: 0 16px 48px rgb(23 43 61 / 6%);
}

.login-intro {
  display: flex;
  flex-direction: column;
  padding: 2.5rem 3rem;
  color: #edf3f7;
  background: #142b3f;
}

.intro-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.18em;

  svg { width: 2rem; height: 2rem; fill: none; stroke: #73d9c3; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
}

.intro-copy { margin-top: 3rem; }
.intro-eyebrow { margin: 0 0 1rem; font-size: 0.875rem; color: #9fb7c8; }
.intro-title { margin: 0; font-size: clamp(2rem, 3.4vw, 3rem); font-weight: 600; line-height: 1.4; letter-spacing: 0.06em; }
.intro-description { margin: 1.25rem 0 0; color: #b7c7d3; font-size: 0.875rem; line-height: 1.9; }
.lab-illustration { display: block; width: 100%; height: auto; margin: 1.5rem 0; }
.intro-capabilities { display: flex; flex-wrap: wrap; gap: 0.75rem 1.5rem; margin: auto 0 0; padding: 1.5rem 0 0; border-top: 1px solid #365065; list-style: none; font-size: 0.8125rem; color: #c7d7e2; }
.intro-capabilities li::before { content: ''; display: inline-block; width: 0.375rem; height: 0.375rem; margin-right: 0.5rem; border-radius: 50%; background: #73d9c3; vertical-align: middle; }

.login-panel { display: flex; flex-direction: column; justify-content: center; min-width: 0; padding: 3rem; }
.login-eyebrow { margin: 0 0 1rem; color: #0f766e; font-size: 0.8125rem; font-weight: 600; letter-spacing: 0.12em; }
.login-heading h1 { margin: 0; color: var(--login-ink); font-size: 1.5rem; line-height: 1.5; font-weight: 600; }
.login-description { margin: 0.75rem 0 2rem; color: var(--login-muted); font-size: 0.875rem; line-height: 1.7; }

.login-form {
  :deep(.el-form-item) { margin-bottom: 1.5rem; }
  :deep(.el-form-item__label) { margin-bottom: 0.5rem; color: var(--login-ink); font-size: 0.875rem; line-height: 1.5; font-weight: 500; }
  :deep(.el-form-item__content) { min-width: 0; }
  :deep(.el-input) { min-width: 0; height: 2.875rem; }
  :deep(.el-input__wrapper) { padding: 0 0.875rem; border-radius: 0.5rem; background: var(--login-surface); box-shadow: 0 0 0 1px var(--login-border) inset; }
  :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 2px #0f766e inset; }
  :deep(.el-input__inner) { color: var(--login-ink); }
  :deep(.el-input__inner::placeholder) { color: var(--login-muted); }
  .input-icon { width: 1rem; height: 1rem; color: var(--login-muted); }
}

.password-toggle { min-width: 2.75rem; min-height: 2.75rem; padding: 0 0.25rem; border: 0; border-radius: 0.25rem; background: transparent; color: #0f766e; font: inherit; font-size: 0.8125rem; cursor: pointer; }
.password-toggle:focus-visible, .captcha-refresh:focus-visible { outline: 2px solid #0f766e; outline-offset: 3px; }
.captcha-field { display: grid; grid-template-columns: minmax(0, 1fr) 7.5rem; gap: 0.75rem; width: 100%; }
.captcha-refresh { display: flex; justify-content: center; align-items: center; height: 2.875rem; min-width: 0; padding: 0.25rem; overflow: hidden; border: 1px solid var(--login-border); border-radius: 0.5rem; color: var(--login-muted); background: var(--login-canvas); font: inherit; font-size: 0.8125rem; cursor: pointer; }
.captcha-refresh:hover:not(:disabled) { border-color: #0f766e; }
.captcha-refresh:disabled { cursor: wait; }
.captcha-image { display: block; max-width: 100%; height: 100%; object-fit: contain; }
.captcha-help { width: 100%; margin: 0.5rem 0 0; color: var(--login-muted); font-size: 0.75rem; line-height: 1.5; }
.captcha-help.is-error { color: var(--el-color-danger, #c2413b); }
.login-submit { width: 100%; min-height: 2.875rem; margin-top: 0.25rem; border-radius: 0.5rem; font-weight: 600; }
.login-submit:focus-visible { outline: 2px solid #0f766e; outline-offset: 3px; }
.login-help { margin: 1.5rem 0 0; color: var(--login-muted); font-size: 0.75rem; line-height: 1.8; }
.login-footer { width: 100%; max-width: 1120px; padding-top: 1.5rem; text-align: center; color: var(--login-muted); font-size: 0.6875rem; line-height: 1.8; overflow-wrap: anywhere; }

html.dark .login {
  --login-canvas: #0e1927;
  --login-surface: #182a3b;
  --login-ink: #e7eef5;
  --login-muted: #afbecc;
  --login-border: #42586b;
  --el-color-primary: #79d8c6;
  --el-color-danger: #ffb4ab;

  .login-eyebrow, .password-toggle { color: #79d8c6; }
  .login-submit:focus-visible, .password-toggle:focus-visible, .captcha-refresh:focus-visible { outline-color: #79d8c6; }
}

@media (max-width: 1023px) {
  .login-intro { padding: 2rem; }
  .login-panel { padding: 2rem; }
}

@media (max-width: 767px) {
  .login { padding: 1.5rem 1rem; }
  .login-shell { display: block; max-width: 28rem; }
  .login-intro { display: none; }
  .login-panel { padding: 2rem 1.5rem; }
  .login-heading h1 { font-size: 1.375rem; }
  .login-description { margin-bottom: 1.5rem; }
  .login-footer { max-width: 28rem; }
}

@media (max-width: 359px) {
  .login { padding: 1rem 0.75rem; }
  .login-panel { padding: 1.75rem 1rem; }
  .login-heading h1 { font-size: 1.25rem; }
  .captcha-field { grid-template-columns: minmax(0, 1fr) 6rem; gap: 0.5rem; }
  .login-form :deep(.el-input__wrapper) { padding: 0 0.5rem; }
}
</style>
