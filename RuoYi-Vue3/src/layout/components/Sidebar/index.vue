<template>
  <div :class="{ 'has-logo': showLogo }" class="sidebar-container">
    <logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :background-color="getMenuBackground"
        :text-color="getMenuTextColor"
        :unique-opened="true"
        :active-text-color="theme"
        :collapse-transition="false"
        mode="vertical"
        :class="['sidebar-menu', sideTheme]"
      >
        <sidebar-item
          v-for="(route, index) in sidebarRouters"
          :key="route.path + index"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup>
import Logo from './Logo'
import SidebarItem from './SidebarItem'
import variables from '@/assets/styles/variables.module.scss'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

const route = useRoute()
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()

const sidebarRouters = computed(() => permissionStore.sidebarRouters)
const showLogo = computed(() => settingsStore.sidebarLogo)
const sideTheme = computed(() => settingsStore.sideTheme)
const theme = computed(() => settingsStore.theme)
const isCollapse = computed(() => !appStore.sidebar.opened)

// 获取菜单背景色
const getMenuBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuBg : variables.menuLightBg
})

// 获取菜单文字颜色
const getMenuTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-text)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuText : variables.menuLightText
})

const activeMenu = computed(() => {
  const { meta, path } = route
  if (meta.activeMenu) {
    return meta.activeMenu
  }
  return path
})
</script>

<style lang="scss" scoped>
.sidebar-container {
  background-color: v-bind(getMenuBackground);

  .scrollbar-wrapper {
    background-color: v-bind(getMenuBackground);
  }

  :deep(.el-scrollbar__view) {
    min-height: 100%;
  }

  :deep(.sidebar-menu) {
    border: none;
    height: 100%;
    width: 100% !important;
    background: transparent !important;
  }

  :deep(.sidebar-menu ul),
  :deep(.sidebar-menu .el-menu) {
    background: transparent !important;
  }

  :deep(.sidebar-menu:not(.el-menu--collapse) .el-menu-item),
  :deep(.sidebar-menu:not(.el-menu--collapse) .el-sub-menu__title) {
    width: calc(100% - 16px);
    margin: 4px 8px;
    border-radius: 12px;
  }

  :deep(.sidebar-menu.el-menu--collapse .el-menu-item),
  :deep(.sidebar-menu.el-menu--collapse .el-sub-menu__title) {
    width: calc(100% - 12px);
    margin: 6px;
    border-radius: 12px;
  }

  :deep(.sidebar-menu .el-menu-item),
  :deep(.sidebar-menu .el-sub-menu__title) {
    height: 44px;
    line-height: 44px;
    color: v-bind(getMenuTextColor);
    border: none;
    transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
  }

  :deep(.sidebar-menu .el-menu-item:hover),
  :deep(.sidebar-menu .el-sub-menu__title:hover) {
    background-color: var(--menu-hover, rgba(0, 0, 0, 0.06)) !important;
  }

  :deep(.sidebar-menu .el-menu-item .el-menu-tooltip__trigger) {
    width: 100%;
    display: flex !important;
    align-items: center;
  }

  :deep(.sidebar-menu .el-menu-item.is-active),
  :deep(.sidebar-menu .el-sub-menu.is-active > .el-sub-menu__title) {
    color: var(--menu-active-text, #405efe) !important;
    background-color: var(--menu-active-bg, rgba(64, 94, 254, 0.1)) !important;
    box-shadow: inset 0 0 0 1px rgba(64, 94, 254, 0.08);
  }

  :deep(.sidebar-menu .el-menu-item.is-active .svg-icon),
  :deep(.sidebar-menu .el-menu-item.is-active .menu-title),
  :deep(.sidebar-menu .el-sub-menu.is-active > .el-sub-menu__title .svg-icon),
  :deep(.sidebar-menu .el-sub-menu.is-active > .el-sub-menu__title .menu-title) {
    color: inherit !important;
  }

  :deep(.sidebar-menu .el-menu-item .svg-icon),
  :deep(.sidebar-menu .el-sub-menu__title .svg-icon) {
    color: currentColor;
  }

  :deep(.sidebar-menu .el-sub-menu .el-menu-item),
  :deep(.sidebar-menu .nest-menu .el-sub-menu > .el-sub-menu__title) {
    min-width: calc(100% - 16px) !important;
  }

  :deep(.sidebar-menu .menu-title) {
    color: inherit;
  }
}
</style>
