import router from '@/router'
import { ElMessageBox, } from 'element-plus'
import { login, logout, getInfo } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { startHeartbeat, stopHeartbeat } from '@/utils/heartbeat'
import { isEmpty } from "@/utils/validate"
import { resolveAvatarUrl } from "@/utils/ruoyi"
import defAva from '@/assets/images/profile.jpg'

const DEFAULT_PASSWORD_VALIDATE_DAYS = 90

const useUserStore = defineStore(
  'user',
  {
    state: () => ({
      token: getToken(),
      id: '',
      name: '',
      nickName: '',
      avatar: '',
      roles: [],
      permissions: [],
      isDefaultModifyPwd: false,
      isPasswordExpired: false,
      passwordValidateDays: DEFAULT_PASSWORD_VALIDATE_DAYS,
      passwordPolicyPromptShown: false
    }),
    getters: {
      mustChangePassword: (state) => state.isDefaultModifyPwd || state.isPasswordExpired
    },
    actions: {
      clearPasswordPolicy() {
        this.isDefaultModifyPwd = false
        this.isPasswordExpired = false
        this.passwordValidateDays = DEFAULT_PASSWORD_VALIDATE_DAYS
        this.passwordPolicyPromptShown = false
      },
      showPasswordPolicyPrompt() {
        if (!this.mustChangePassword || this.passwordPolicyPromptShown) {
          return
        }
        this.passwordPolicyPromptShown = true
        const message = this.isDefaultModifyPwd
          ? '您的密码仍为初始密码，请先修改密码后再继续使用系统。'
          : `您的密码已超过 ${this.passwordValidateDays || DEFAULT_PASSWORD_VALIDATE_DAYS} 天未修改，请先修改密码后再继续使用系统。`
        ElMessageBox.alert(message, '安全提示', {
          confirmButtonText: '去修改',
          type: 'warning',
          showClose: false,
          closeOnClickModal: false,
          closeOnPressEscape: false
        }).then(() => {
          const currentRoute = router.currentRoute.value
          if (currentRoute.name !== 'Profile' || currentRoute.params?.activeTab !== 'resetPwd') {
            router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } }).catch(() => {})
          }
        }).catch(() => {})
      },
      // 登录
      login(userInfo) {
        const username = userInfo.username.trim()
        const password = userInfo.password
        const code = userInfo.code
        const uuid = userInfo.uuid
        return new Promise((resolve, reject) => {
          login(username, password, code, uuid).then(res => {
            if (!res?.token) {
              reject(new Error(res?.msg || '登录失败'))
              return
            }
            this.clearPasswordPolicy()
            setToken(res.token)
            this.token = res.token
            startHeartbeat()
            resolve(res)
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 获取用户信息
      getInfo() {
        return new Promise((resolve, reject) => {
          getInfo().then(async res => {
            const user = res.user
            let avatar = user.avatar || ""
            avatar = isEmpty(avatar) ? defAva : await resolveAvatarUrl(avatar)
            if (res.roles && res.roles.length > 0) { // 验证返回的roles是否是一个非空数组
              this.roles = res.roles
              this.permissions = res.permissions
            } else {
              this.roles = ['ROLE_DEFAULT']
            }
            this.id = user.userId
            this.name = user.userName
            this.nickName = user.nickName
            this.avatar = avatar
            this.isDefaultModifyPwd = !!res.isDefaultModifyPwd
            this.isPasswordExpired = !!res.isPasswordExpired
            this.passwordValidateDays = Number(res.passwordValidateDays) > 0
              ? Number(res.passwordValidateDays)
              : DEFAULT_PASSWORD_VALIDATE_DAYS
            if (!this.mustChangePassword) {
              this.passwordPolicyPromptShown = false
            }
            this.showPasswordPolicyPrompt()
            resolve(res)
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 退出系统
      logOut() {
        return new Promise((resolve, reject) => {
          logout(this.token).then(() => {
            this.token = ''
            this.roles = []
            this.permissions = []
            this.clearPasswordPolicy()
            removeToken()
            stopHeartbeat()
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      }
    }
  })

export default useUserStore
