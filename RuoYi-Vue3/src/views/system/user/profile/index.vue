<template>
  <div class="app-container profile-page">
    <el-row :gutter="20">
      <el-col :span="7" :xs="24">
        <div class="profile-card">
          <div class="profile-card-header">
            <div class="profile-avatar-wrap">
              <userAvatar />
            </div>
            <div class="profile-main">
              <p class="profile-name">{{ state.user.nickName || state.user.userName || "未设置用户名称" }}</p>
              <p class="profile-sub">{{ state.user.userName || "--" }}</p>
            </div>
          </div>

          <div class="profile-content">
            <div class="profile-row">
              <span class="profile-label"><svg-icon icon-class="peoples" /> 用户名称</span>
              <span class="profile-value">{{ state.user.nickName || "--" }}</span>
            </div>
            <div class="profile-row">
              <span class="profile-label"><svg-icon icon-class="user" /> 工号</span>
              <span class="profile-value">{{ state.user.userName || "--" }}</span>
            </div>
            <div class="profile-row">
              <span class="profile-label"><svg-icon icon-class="phone" /> 手机号码</span>
              <span class="profile-value">{{ state.user.phonenumber || "--" }}</span>
            </div>
            <div class="profile-row">
              <span class="profile-label"><svg-icon icon-class="email" /> 用户邮箱</span>
              <span class="profile-value">{{ state.user.email || "--" }}</span>
            </div>
            <div class="profile-row">
              <span class="profile-label"><svg-icon icon-class="peoples" /> 所属角色</span>
              <span class="profile-value">{{ state.roleGroup || "--" }}</span>
            </div>
            <div class="profile-row">
              <span class="profile-label"><svg-icon icon-class="date" /> 创建日期</span>
              <span class="profile-value">{{ state.user.createTime || "--" }}</span>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :span="17" :xs="24">
        <el-card class="detail-card">
          <template #header>
            <div class="card-title">基本资料</div>
          </template>
          <el-tabs v-model="selectedTab" class="profile-tabs">
            <el-tab-pane label="基本资料" name="userinfo">
              <userInfo :user="state.user" />
            </el-tab-pane>
            <el-tab-pane label="修改密码" name="resetPwd">
              <resetPwd />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="Profile">
import userAvatar from "./userAvatar"
import userInfo from "./userInfo"
import resetPwd from "./resetPwd"
import { getUserProfile } from "@/api/system/user"

const route = useRoute()
const selectedTab = ref("userinfo")
const state = reactive({
  user: {},
  roleGroup: {},
  postGroup: {}
})

function getUser() {
  getUserProfile().then(response => {
    state.user = response.data
    state.roleGroup = response.roleGroup
    state.postGroup = response.postGroup
  })
}

onMounted(() => {
  const activeTab = route.params && route.params.activeTab
  if (activeTab) {
    selectedTab.value = activeTab
  }
  getUser()
})
</script>

<style lang="scss" scoped>
.profile-page {
  .profile-card {
    border-radius: 28px;
    background: linear-gradient(145deg, #8fd2ff, #58aefd);
    box-shadow: 0 18px 32px rgba(88, 174, 253, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.24);
    color: #163b66;
    padding: 24px;
    min-height: 100%;
    position: relative;
    overflow: hidden;
  }

  .profile-card::before,
  .profile-card::after {
    content: "";
    position: absolute;
    border-radius: 50%;
    pointer-events: none;
  }

  .profile-card::before {
    width: 150px;
    height: 150px;
    right: -40px;
    top: -45px;
    background: rgba(255, 255, 255, 0.12);
  }

  .profile-card::after {
    width: 110px;
    height: 110px;
    left: -32px;
    bottom: -35px;
    background: rgba(255, 255, 255, 0.08);
  }

  .profile-card-header {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 16px;
  }

  .profile-avatar-wrap :deep(.user-info-head) {
    height: auto;
  }

  .profile-avatar-wrap :deep(.img-circle) {
    width: 56px;
    height: 56px;
    border-radius: 16px;
    border: 2px solid rgba(255, 255, 255, 0.85);
    box-shadow: 0 6px 14px rgba(0, 0, 0, 0.15);
  }

  .profile-main {
    min-width: 0;
  }

  .profile-name {
    margin: 0;
    font-size: 20px;
    font-weight: 700;
    line-height: 1.2;
    color: #0f3158;
  }

  .profile-sub {
    margin: 6px 0 0;
    color: #2e5685;
    font-size: 13px;
    letter-spacing: 0.4px;
  }

  .profile-content {
    position: relative;
    z-index: 1;
    display: grid;
    gap: 10px;
  }

  .profile-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 12px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.16);
    backdrop-filter: blur(2px);
  }

  .profile-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #1c4b79;
    white-space: nowrap;
  }

  .profile-value {
    font-size: 13px;
    font-weight: 600;
    color: #133b66;
    text-align: right;
    word-break: break-all;
  }

  .detail-card {
    border-radius: 18px;
  }

  .card-title {
    font-size: 16px;
    font-weight: 700;
    color: #303133;
  }

  @media (max-width: 767px) {
    .detail-card {
      margin-top: 16px;
    }

    .profile-row {
      flex-direction: column;
      align-items: flex-start;
      gap: 6px;
    }

    .profile-value {
      text-align: left;
    }
  }
}
</style>
