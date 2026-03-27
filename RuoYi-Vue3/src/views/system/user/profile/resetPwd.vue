<template>
  <el-form ref="pwdRef" :model="user" :rules="rules" label-width="80px">
    <el-form-item label="旧密码" prop="oldPassword">
      <el-input v-model="user.oldPassword" placeholder="请输入旧密码" type="password" show-password />
    </el-form-item>
    <el-form-item label="新密码" prop="newPassword">
      <el-input v-model="user.newPassword" placeholder="请输入新密码" type="password" show-password />
    </el-form-item>
    <el-form-item label="确认密码" prop="confirmPassword">
      <el-input v-model="user.confirmPassword" placeholder="请确认新密码" type="password" show-password />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="submit">保存</el-button>
      <el-button type="danger" @click="close">关闭</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { updateUserPwd } from "@/api/system/user"

const { proxy } = getCurrentInstance()
const pwdRef = ref()

const user = reactive({
  oldPassword: undefined,
  newPassword: undefined,
  confirmPassword: undefined
})

function getPasswordProfile(password = "") {
  const hasLetter = /[A-Za-z]/.test(password)
  const hasNumber = /\d/.test(password)
  const hasSpecial = /[^A-Za-z0-9<>"'|\\\s]/.test(password)

  return {
    hasLetter,
    hasNumber,
    hasSpecial
  }
}

const validatePasswordComplexity = (rule, value, callback) => {
  if (!value || value.length < 6 || value.length > 20) {
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
  if (user.newPassword !== value) {
    callback(new Error("两次输入的密码不一致"))
  } else {
    callback()
  }
}

const rules = ref({
  oldPassword: [{ required: true, message: "旧密码不能为空", trigger: "blur" }],
  newPassword: [
    { required: true, message: "新密码不能为空", trigger: "blur" },
    { min: 6, max: 20, message: "长度在 6 到 20 个字符", trigger: "blur" },
    { pattern: /^[^<>"'|\\]+$/, message: "不能包含非法字符：< > \" ' \\ |", trigger: "blur" },
    { validator: validatePasswordComplexity, trigger: "blur" }
  ],
  confirmPassword: [
    { required: true, message: "确认密码不能为空", trigger: "blur" },
    { required: true, validator: equalToPassword, trigger: "blur" }
  ]
})

watch(() => user.newPassword, () => {
  if (user.confirmPassword) {
    pwdRef.value?.validateField("confirmPassword")
  }
})

function submit() {
  pwdRef.value?.validate(valid => {
    if (valid) {
      updateUserPwd(user.oldPassword, user.newPassword).then(() => {
        proxy.$modal.msgSuccess("修改成功")
      })
    }
  })
}

function close() {
  proxy.$tab.closePage()
}
</script>
