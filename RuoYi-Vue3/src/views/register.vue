<template>
  <div class="register">
    <div class="register-card">
      <el-form ref="registerRef" :model="registerForm" :rules="registerRules" class="register-form" autocomplete="off">
      <div class="form">
        <p id="heading">{{ title }}</p>
        <input class="register-autofill-guard" type="text" name="fake_username" autocomplete="username" tabindex="-1" />
        <input class="register-autofill-guard" type="password" name="fake_password" autocomplete="current-password" tabindex="-1" />

        <el-form-item prop="username" class="custom-form-item">
          <el-input
            v-model="registerForm.username"
            type="text"
            size="large"
            name="register_username"
            autocomplete="off"
            placeholder="工号"
          >
            <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="nickName" class="custom-form-item">
          <el-input
            v-model="registerForm.nickName"
            type="text"
            size="large"
            name="register_nick_name"
            autocomplete="off"
            placeholder="用户名称"
          >
            <template #prefix><svg-icon icon-class="peoples" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password" class="custom-form-item password-form-item">
          <el-input
            v-model="registerForm.password"
            type="password"
            size="large"
            name="register_password"
            autocomplete="new-password"
            placeholder="密码"
            @keyup.enter="handleRegister"
          >
            <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
            <template #suffix>
              <span
                v-if="passwordStrength"
                class="password-strength"
                :class="passwordStrength.className"
              >
                {{ passwordStrength.label }}
              </span>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="confirmPassword" class="custom-form-item">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            size="large"
            name="register_confirm_password"
            autocomplete="new-password"
            placeholder="确认密码"
            @keyup.enter="handleRegister"
          >
            <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="code" v-if="captchaEnabled" class="custom-form-item captcha-form-item">
          <el-input
            size="large"
            v-model="registerForm.code"
            name="register_code"
            autocomplete="off"
            placeholder="验证码"
            @keyup.enter="handleRegister"
          >
            <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
          </el-input>
          <div class="register-code">
            <img :src="codeUrl" @click="getCode" class="register-code-img" alt="验证码" />
          </div>
        </el-form-item>

        <el-form-item class="custom-form-item register-action-item">
          <el-button
            :loading="loading"
            size="large"
            type="primary"
            @click.prevent="handleRegister"
          >
            <span v-if="!loading">注册</span>
            <span v-else>注册中...</span>
          </el-button>
          <div class="register-extra">
            <router-link class="link-type" :to="'/login'">使用已有账户登录</router-link>
          </div>
        </el-form-item>
      </div>
      </el-form>
    </div>

    <div class="el-register-footer">
      <span>{{ footerContent }}</span>
    </div>
  </div>
</template>

<script setup>
import { ElMessageBox } from "element-plus"
import { getCodeImg, register } from "@/api/login"
import defaultSettings from "@/settings"

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent
const router = useRouter()
const registerRef = ref()

const registerForm = ref({
  username: "",
  nickName: "",
  password: "",
  confirmPassword: "",
  code: "",
  uuid: ""
})

const passwordStrengthLevels = {
  weak: { label: "弱", className: "is-weak" },
  medium: { label: "中", className: "is-medium" },
  strong: { label: "强", className: "is-strong" }
}

function getPasswordProfile(password = "") {
  const hasLetter = /[A-Za-z]/.test(password)
  const hasNumber = /\d/.test(password)
  const hasSpecial = /[^A-Za-z0-9<>"'|\\\s]/.test(password)
  const hasMixedCase = /[a-z]/.test(password) && /[A-Z]/.test(password)

  return {
    hasLetter,
    hasNumber,
    hasSpecial,
    hasMixedCase,
    typeCount: [hasLetter, hasNumber, hasSpecial].filter(Boolean).length
  }
}

const passwordStrength = computed(() => {
  const password = registerForm.value.password

  if (!password || password.length < 5) {
    return null
  }

  const { typeCount, hasMixedCase } = getPasswordProfile(password)

  if (typeCount <= 1 || password.length < 8) {
    return passwordStrengthLevels.weak
  }

  if (typeCount === 2 || password.length < 10) {
    return passwordStrengthLevels.medium
  }

  return hasMixedCase || password.length >= 12
    ? passwordStrengthLevels.strong
    : passwordStrengthLevels.medium
})

const validatePasswordComplexity = (rule, value, callback) => {
  if (!value || value.length < 5 || value.length > 20) {
    callback()
    return
  }

  const { hasLetter, hasNumber, hasSpecial } = getPasswordProfile(value)

  if (!hasLetter || !hasNumber || !hasSpecial) {
    callback(new Error("密码必须同时包含字母、数字和特殊字符"))
    return
  }

  callback()
}

const equalToPassword = (rule, value, callback) => {
  if (registerForm.value.password !== value) {
    callback(new Error("两次输入的密码不一致"))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, trigger: "blur", message: "请输入您的工号" },
    { min: 2, max: 20, message: "工号长度必须介于 2 和 20 之间", trigger: "blur" }
  ],
  nickName: [
    { required: true, trigger: "blur", message: "请输入用户名称" },
    { min: 1, max: 30, message: "用户名称长度必须介于 1 和 30 之间", trigger: "blur" }
  ],
  password: [
    { required: true, trigger: "blur", message: "请输入您的密码" },
    { min: 5, max: 20, message: "用户密码长度必须介于 5 和 20 之间", trigger: "blur" },
    { pattern: /^[^<>"'|\\]+$/, message: "不能包含非法字符：< > \" ' \\ |", trigger: "blur" },
    { validator: validatePasswordComplexity, trigger: "blur" }
  ],
  confirmPassword: [
    { required: true, trigger: "blur", message: "请再次输入您的密码" },
    { required: true, validator: equalToPassword, trigger: "blur" }
  ],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
const captchaEnabled = ref(true)

watch(() => registerForm.value.password, () => {
  if (registerForm.value.confirmPassword) {
    registerRef.value?.validateField("confirmPassword")
  }
})

function handleRegister() {
  registerRef.value?.validate(valid => {
    if (valid) {
      loading.value = true
      register(registerForm.value).then(res => {
        const username = registerForm.value.username
        ElMessageBox.alert(`<font color='red'>恭喜你，您的工号 ${username} 注册成功！</font>`, "系统提示", {
          dangerouslyUseHTMLString: true,
          type: "success",
        }).then(() => {
          router.push("/login")
        }).catch(() => {})
      }).catch(() => {
        loading.value = false
        if (captchaEnabled) {
          getCode()
        }
      })
    }
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      registerForm.value.uuid = res.uuid
    }
  })
}

getCode()
</script>

<style lang="scss" scoped>
.register {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(135deg, #0d1016, #141923 48%, #0c0f15);
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 32px 16px 72px;
}

.register-card {
  width: min(520px, 100%);
  min-height: 620px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
  background: linear-gradient(0deg, #000, #272727);
  border-radius: 22px;
  position: relative;
  isolation: isolate;
  box-shadow: 0 22px 44px rgba(0, 0, 0, 0.28);
}

.register-card::before {
  content: "";
  position: absolute;
  inset: 0;
  padding: 2px;
  border-radius: inherit;
  background: linear-gradient(45deg, #fb0094, #0000ff, #00ff00, #ffff00, #ff0000, #fb0094, #0000ff, #00ff00, #ffff00, #ff0000);
  background-size: 400%;
  z-index: -1;
  animation: steam 20s linear infinite;
  -webkit-mask:
    linear-gradient(#fff 0 0) content-box,
    linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
}

.register-form {
  width: 100%;
  max-width: 420px;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  padding: 2em;
  background:
    linear-gradient(180deg, rgba(18, 20, 26, 0.92), rgba(12, 14, 19, 0.96));
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 18px 40px rgba(0, 0, 0, 0.35);
  transition: transform 0.3s ease, box-shadow 0.3s ease, border-color 0.3s ease;
}

.form:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 255, 255, 0.14);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 22px 48px rgba(0, 0, 0, 0.42);
}

#heading {
  text-align: center;
  margin: 0 0 8px;
  color: #fff;
  font-size: 1.5em;
  font-weight: 700;
}

.register-autofill-guard {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: 0;
  border: 0;
  opacity: 0;
  pointer-events: none;
}

.custom-form-item {
  margin-bottom: 0;
}

:deep(.custom-form-item .el-form-item__content) {
  display: block;
}

:deep(.custom-form-item .el-form-item__error) {
  position: static;
  padding-top: 6px;
  color: #ff6b6b;
}

:deep(.register-form .el-input) {
  --el-input-hover-border: transparent;
}

:deep(.register-form .el-input__wrapper) {
  min-height: 48px;
  padding: 0 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 1px 2px rgba(255, 255, 255, 0.04) !important;
  border: 1px solid transparent;
}

:deep(.register-form .el-input__wrapper.is-focus) {
  box-shadow: inset 0 1px 2px rgba(255, 255, 255, 0.04), 0 0 0 1px rgba(76, 158, 255, 0.9) !important;
}

:deep(.register-form .el-input__inner) {
  height: 48px;
  color: #d3d3d3;
  font-size: 14px;
}

:deep(.register-form .el-input__inner::placeholder) {
  color: #8f96a3;
}

.input-icon {
  width: 1.2em;
  height: 1.2em;
  color: #fff;
}

:deep(.password-form-item .el-input__suffix-inner) {
  display: flex;
  align-items: center;
}

.password-strength {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  transition: background 0.2s ease, color 0.2s ease;
}

.password-strength.is-weak {
  color: #ffb4b4;
  background: rgba(255, 107, 107, 0.18);
}

.password-strength.is-medium {
  color: #ffd27d;
  background: rgba(255, 184, 77, 0.18);
}

.password-strength.is-strong {
  color: #8ff0c0;
  background: rgba(43, 199, 118, 0.2);
}

:deep(.captcha-form-item .el-form-item__content) {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

:deep(.captcha-form-item .el-input) {
  flex: 1;
}

.register-code {
  width: 120px;
  height: 48px;
  border-radius: 14px;
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.register-code-img {
  width: 100%;
  height: 100%;
  display: block;
  cursor: pointer;
  object-fit: cover;
}

.register-action-item {
  margin-top: 10px;
}

:deep(.register-action-item .el-form-item__content) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

:deep(.register-action-item .el-button) {
  width: 100%;
  height: 44px;
  margin-left: 0;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #121722, #242a37);
  color: #fff;
  font-weight: 600;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
  transition: 0.3s ease-in-out;
}

:deep(.register-action-item .el-button:hover) {
  background: linear-gradient(135deg, #1a2230, #2f3848);
  color: #fff;
}

:deep(.register-action-item .el-button.is-loading) {
  opacity: 0.85;
}

.register-extra {
  display: flex;
  justify-content: flex-end;
}

.link-type {
  color: #87b8ff;
  text-decoration: none;
  font-size: 14px;
}

.link-type:hover {
  text-decoration: underline;
}

.el-register-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #fff;
  font-family: Arial;
  font-size: 12px;
  letter-spacing: 1px;
}

@keyframes steam {
  0% {
    background-position: 0 0;
  }

  50% {
    background-position: 400% 0;
  }

  100% {
    background-position: 0 0;
  }
}

@media (max-width: 520px) {
  .register {
    padding: 24px 12px 72px;
  }

  .register-card {
    min-height: auto;
    padding: 14px;
    border-radius: 18px;
  }

  .form {
    padding: 1.5em;
    border-radius: 16px;
  }

  :deep(.captcha-form-item .el-form-item__content) {
    flex-direction: column;
  }

  .register-code {
    width: 100%;
  }
}
</style>
