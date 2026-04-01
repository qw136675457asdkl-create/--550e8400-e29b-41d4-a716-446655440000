<template>
  <section class="app-main">
    <div class="app-main__viewport">
      <div class="app-main__content">
        <router-view v-slot="{ Component, route }">
          <transition name="fade-transform" mode="out-in">
            <keep-alive :include="tagsViewStore.cachedViews">
              <component v-if="!route.meta.link" :is="Component" :key="route.path" />
            </keep-alive>
          </transition>
        </router-view>
      </div>
    </div>
    <iframe-toggle />
    <copyright />
  </section>
</template>

<script setup>
import copyright from "./Copyright/index"
import iframeToggle from "./IframeToggle/index"
import useTagsViewStore from '@/store/modules/tagsView'

const route = useRoute()
const tagsViewStore = useTagsViewStore()

onMounted(() => {
  addIframe()
})

watchEffect(() => {
  addIframe()
})

function addIframe() {
  if (route.meta.link) {
    useTagsViewStore().addIframeView(route)
  }
}
</script>

<style lang="scss" scoped>
.app-main {
  min-height: calc(100vh - 60px);
  width: 100%;
  position: relative;
  overflow: hidden;
  background: var(--app-main-bg, #f1f5f9);
}

.app-main__viewport {
  min-height: 100%;
  padding: 20px;
  background: var(--app-main-bg, #f1f5f9);
}

.app-main__content {
  min-height: 100%;
}

.app-main__content :deep(.app-container) {
  padding: 0;
}

.fixed-header + .app-main {
  overflow-y: auto;
  scrollbar-gutter: auto;
  height: calc(100vh - 60px);
  min-height: 0px;
}

.app-main:has(.copyright) {
  padding-bottom: 36px;
}

.fixed-header + .app-main {
  margin-top: 60px;
}

.hasTagsView {
  .app-main {
    min-height: calc(100vh - 94px);
  }

  .fixed-header + .app-main {
    margin-top: 94px;
    height: calc(100vh - 94px);
    min-height: 0px;
  }
}

@media screen and (max-width: 991px) {
  .app-main__viewport {
    padding: 16px;
  }

  .fixed-header + .app-main {
    padding-bottom: max(60px, calc(constant(safe-area-inset-bottom) + 40px));
    padding-bottom: max(60px, calc(env(safe-area-inset-bottom) + 40px));
    overscroll-behavior-y: none;
  }

  .hasTagsView .fixed-header + .app-main {
    padding-bottom: max(60px, calc(constant(safe-area-inset-bottom) + 40px));
    padding-bottom: max(60px, calc(env(safe-area-inset-bottom) + 40px));
    overscroll-behavior-y: none;
  }
}

@supports (-webkit-touch-callout: none) {
  @media screen and (max-width: 991px) {
    .fixed-header + .app-main {
      padding-bottom: max(17px, calc(constant(safe-area-inset-bottom) + 10px));
      padding-bottom: max(17px, calc(env(safe-area-inset-bottom) + 10px));
      height: calc(100svh - 60px);
      height: calc(100dvh - 60px);
    }

    .hasTagsView .fixed-header + .app-main {
      padding-bottom: max(17px, calc(constant(safe-area-inset-bottom) + 10px));
      padding-bottom: max(17px, calc(env(safe-area-inset-bottom) + 10px));
      height: calc(100svh - 94px);
      height: calc(100dvh - 94px);
    }
  }
}
</style>

<style lang="scss">
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background-color: #f1f1f1;
}

::-webkit-scrollbar-thumb {
  background-color: #c0c0c0;
  border-radius: 3px;
}
</style>
