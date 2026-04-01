<template>
  <div class="navbar" :class="'nav' + settingsStore.navType">
    <div class="navbar-main">
      <hamburger id="hamburger-container" :is-active="appStore.sidebar.opened" class="hamburger-container" @toggleClick="toggleSideBar" />
      <breadcrumb v-if="settingsStore.navType == 1" id="breadcrumb-container" class="breadcrumb-container" />
      <top-nav v-if="settingsStore.navType == 2" id="topmenu-container" class="topmenu-container" />
      <template v-if="settingsStore.navType == 3">
        <logo v-show="settingsStore.sidebarLogo" :collapse="false" class="navbar-logo" />
        <top-bar id="topbar-container" class="topbar-container" />
      </template>
    </div>

    <div class="right-menu">
      <div v-if="appStore.device !== 'mobile'" class="right-menu-actions">
        <header-search id="header-search" class="right-menu-item hover-effect" />

        <screenfull id="screenfull" class="right-menu-item hover-effect" />

        <el-tooltip content="布局大小" effect="dark" placement="bottom">
          <size-select id="size-select" class="right-menu-item hover-effect" />
        </el-tooltip>

        <div class="right-menu-item clock-wrapper" :title="'统一时间源：' + (serverZoneId || '服务器本地时区')">
          <span class="clock-value">{{ serverClockText }}</span>
        </div>
      </div>

      <el-dropdown @command="handleCommand" class="avatar-container right-menu-item" trigger="hover">
        <div class="avatar-wrapper">
          <img :src="userStore.avatar" class="user-avatar" />
          <span class="user-nickname">{{ userStore.nickName }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <router-link to="/user/profile">
              <el-dropdown-item>个人中心</el-dropdown-item>
            </router-link>
            <el-dropdown-item command="setLayout" v-if="settingsStore.showSettings">
              <span>布局设置</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <div class="logout-btn-wrapper">
        <button class="Btn" @click="logout" title="退出登录">
          <div class="sign">
            <svg viewBox="0 0 512 512">
              <path d="M377.9 105.9L500.7 228.7c7.2 7.2 11.3 17.1 11.3 27.3s-4.1 20.1-11.3 27.3L377.9 406.1c-6.4 6.4-15 9.9-24 9.9c-18.7 0-33.9-15.2-33.9-33.9l0-62.1-128 0c-17.7 0-32-14.3-32-32l0-64c0-17.7 14.3-32 32-32l128 0 0-62.1c0-18.7 15.2-33.9 33.9-33.9c9 0 17.6 3.6 24 9.9zM160 96L96 96c-17.7 0-32 14.3-32 32l0 256c0 17.7 14.3 32 32 32l64 0c17.7 0 32 14.3 32 32s-14.3 32-32 32l-64 0c-53 0-96-43-96-96L0 128C0 75 43 32 96 32l64 0c17.7 0 32 14.3 32 32s-14.3 32-32 32z"></path>
            </svg>
          </div>
          <div class="text">退出登录</div>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb'
import TopNav from '@/components/TopNav'
import TopBar from './TopBar'
import Logo from './Sidebar/Logo'
import Hamburger from '@/components/Hamburger'
import Screenfull from '@/components/Screenfull'
import SizeSelect from '@/components/SizeSelect'
import HeaderSearch from '@/components/HeaderSearch'
import useAppStore from '@/store/modules/app'
import useUserStore from '@/store/modules/user'
import useSettingsStore from '@/store/modules/settings'
import { getServerTime } from '@/api/system/time'

const appStore = useAppStore()
const userStore = useUserStore()
const settingsStore = useSettingsStore()
const serverClockText = ref('')
const serverZoneId = ref('')
const serverOffsetMs = ref(0)
let clockTickTimer = null
let clockSyncTimer = null

function pad2(value) {
  return String(value).padStart(2, '0')
}

function formatClock(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

function updateClockText() {
  const correctedNow = new Date(Date.now() + serverOffsetMs.value)
  serverClockText.value = formatClock(correctedNow)
}

async function syncServerClock() {
  try {
    const requestStart = Date.now()
    const response = await getServerTime()
    const requestEnd = Date.now()
    const serverMillis = Number(response?.data?.epochMillis)
    if (!Number.isNaN(serverMillis)) {
      const roundTrip = requestEnd - requestStart
      serverOffsetMs.value = serverMillis + Math.floor(roundTrip / 2) - requestEnd
      serverZoneId.value = response?.data?.zoneId || ''
    }
  } catch (error) {
    // 网络异常时维持当前偏移，避免时钟跳变
  } finally {
    updateClockText()
  }
}

function toggleSideBar() {
  appStore.toggleSideBar()
}

function handleCommand(command) {
  switch (command) {
    case "setLayout":
      setLayout()
      break
    default:
      break
  }
}

function logout() {
  ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logOut().then(() => {
      location.href = '/index'
    })
  }).catch(() => { })
}

const emits = defineEmits(['setLayout'])
function setLayout() {
  emits('setLayout')
}

onMounted(() => {
  updateClockText()
  syncServerClock()
  clockTickTimer = setInterval(updateClockText, 1000)
  clockSyncTimer = setInterval(syncServerClock, 60000)
})

onBeforeUnmount(() => {
  if (clockTickTimer) {
    clearInterval(clockTickTimer)
    clockTickTimer = null
  }
  if (clockSyncTimer) {
    clearInterval(clockSyncTimer)
    clockSyncTimer = null
  }
})
</script>

<style lang='scss' scoped>
.navbar.nav3 {
  .hamburger-container {
    display: none !important;
  }
}

.navbar {
  height: 60px;
  position: relative;
  background: var(--navbar-surface, var(--navbar-bg));
  border-bottom: 1px solid var(--navbar-border, rgba(226, 232, 240, 0.88));
  backdrop-filter: blur(18px);
  display: flex;
  align-items: center;
  padding: 0 18px 0 14px;
  box-sizing: border-box;

  .navbar-main {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 12px;
    overflow: hidden;
  }

  .hamburger-container {
    width: 40px;
    height: 40px;
    margin-right: 2px;
    border-radius: 50%;
    -webkit-tap-highlight-color: transparent;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    color: var(--navbar-text);
    cursor: pointer;
    transition: background-color 0.2s ease, transform 0.2s ease;

    &:hover {
      background: var(--navbar-hover-soft, rgba(148, 163, 184, 0.14));
      transform: translateY(-1px);
    }
  }

  .breadcrumb-container {
    height: 100%;
    display: flex;
    align-items: center;
    flex-shrink: 0;
  }

  .topmenu-container {
    flex: 1;
    min-width: 0;
    position: static;
  }

  .navbar-logo {
    flex-shrink: 0;
  }

  .topbar-container {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    overflow: hidden;
  }

  .errLog-container {
    display: inline-block;
    vertical-align: top;
  }

  .right-menu {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-left: auto;
    flex-shrink: 0;
    height: 100%;

    &:focus {
      outline: none;
    }

    .right-menu-actions {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .right-menu-item {
      min-height: 40px;
      color: var(--navbar-text);
      font-size: 16px;
      display: inline-flex;
      align-items: center;
      justify-content: center;

      &.hover-effect {
        width: 40px;
        min-width: 40px;
        height: 40px;
        padding: 0;
        border-radius: 50%;
        cursor: pointer;
        transition: background-color 0.2s ease, transform 0.2s ease, color 0.2s ease;

        &:hover {
          background: var(--navbar-hover-soft, rgba(148, 163, 184, 0.14));
          transform: translateY(-1px);
        }
      }

      &.clock-wrapper {
        cursor: default;
        min-height: 40px;
        padding: 0 12px;
        border-radius: 999px;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: flex-end;
        line-height: 1.1;
        min-width: 184px;
        background: rgba(148, 163, 184, 0.1);
        color: var(--navbar-text);

        .clock-value {
          font-size: 14px;
          font-weight: 600;
          font-variant-numeric: tabular-nums;
          letter-spacing: 0.2px;
        }
      }
    }

    .avatar-container {
      padding: 0;
      min-height: 40px;

      .avatar-wrapper {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 6px 10px 6px 6px;
        border-radius: 999px;
        transition: background-color 0.2s ease, transform 0.2s ease;

        &:hover {
          background: var(--navbar-hover-soft, rgba(148, 163, 184, 0.14));
          transform: translateY(-1px);
        }

        .user-avatar {
          cursor: pointer;
          width: 32px;
          height: 32px;
          border-radius: 50%;
          object-fit: cover;
        }

        .user-nickname {
          font-size: 14px;
          font-weight: 600;
          color: var(--navbar-text);
        }
      }
    }
  }
}

.logout-btn-wrapper {
  display: flex;
  align-items: center;
  margin-left: 2px;
}

.Btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  height: 40px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  position: relative;
  overflow: visible;
  transition: background-color 0.2s ease, transform 0.2s ease;
  box-shadow: none;
  background-color: rgba(239, 68, 68, 0.12);
}

.sign {
  display: flex;
  align-items: center;
  justify-content: center;
}

.sign svg {
  width: 15px;
}

.sign svg path {
  fill: #dc2626;
}

.text {
  position: static;
  width: auto;
  opacity: 1;
  color: #dc2626;
  font-size: 13px;
  font-weight: 600;
  margin-left: 8px;
}

.Btn:hover {
  background-color: rgba(239, 68, 68, 0.18);
  transform: translateY(-1px);
}

.Btn:hover .sign {
  padding-left: 0;
}

.Btn:hover .text {
  width: auto;
  padding-right: 0;
}

.Btn:active {
  transform: translateY(0);
}

:deep(.app-breadcrumb.el-breadcrumb) {
  line-height: 1.2;
}

:deep(.app-breadcrumb .el-breadcrumb__inner),
:deep(.app-breadcrumb .el-breadcrumb__inner a) {
  color: var(--navbar-text);
}

:deep(.topmenu-container.el-menu--horizontal),
:deep(.topbar-container.topbar-menu.el-menu--horizontal) {
  border-bottom: none !important;
  background: transparent !important;
}

:deep(.topmenu-container.el-menu--horizontal > .el-menu-item),
:deep(.topmenu-container.el-menu--horizontal > .el-sub-menu .el-sub-menu__title),
:deep(.topbar-container.topbar-menu.el-menu--horizontal > .el-menu-item),
:deep(.topbar-container.topbar-menu.el-menu--horizontal > .el-sub-menu .el-sub-menu__title) {
  height: 60px !important;
  line-height: 60px !important;
  color: var(--navbar-text) !important;
  margin: 0 8px !important;
  border-bottom: none !important;
  background: transparent !important;
}

:deep(.topmenu-container.el-menu--horizontal > .el-menu-item.is-active),
:deep(.topmenu-container.el-menu--horizontal > .el-sub-menu.is-active .el-sub-menu__title),
:deep(.topbar-container.topbar-menu.el-menu--horizontal > .el-menu-item.is-active),
:deep(.topbar-container.topbar-menu.el-menu--horizontal > .el-sub-menu.is-active .el-sub-menu__title) {
  color: var(--menu-active-text, #405efe) !important;
  box-shadow: inset 0 -2px 0 var(--menu-active-text, #405efe);
}

:deep(.topmenu-container.el-menu--horizontal > .el-menu-item:not(.is-disabled):hover),
:deep(.topmenu-container.el-menu--horizontal > .el-sub-menu .el-sub-menu__title:hover),
:deep(.topbar-container.topbar-menu.el-menu--horizontal > .el-menu-item:not(.is-disabled):hover),
:deep(.topbar-container.topbar-menu.el-menu--horizontal > .el-sub-menu .el-sub-menu__title:hover) {
  background: transparent !important;
}

:deep(.right-menu-item .header-search),
:deep(.right-menu-item > div),
:deep(.right-menu-item .el-dropdown) {
  width: 100%;
  height: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

:deep(.header-search .search-icon),
:deep(.right-menu-item .svg-icon) {
  font-size: 18px;
}

:deep(.size-icon--style) {
  font-size: 18px;
  line-height: 1;
  padding-right: 0;
}

@media screen and (max-width: 1360px) {
  .navbar {
    .right-menu .clock-wrapper {
      display: none;
    }
  }
}

@media screen and (max-width: 991px) {
  .navbar {
    padding: 0 14px 0 12px;

    .right-menu {
      gap: 6px;
    }

    .avatar-container .avatar-wrapper {
      padding-right: 6px;
    }

    .avatar-container .user-nickname,
    .Btn .text {
      display: none;
    }
  }
}
</style>
