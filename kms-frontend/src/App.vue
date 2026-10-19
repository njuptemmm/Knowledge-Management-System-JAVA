<script setup lang="ts">
// 只提供全局的 naive-ui provider 与路由出口。
// 业务页面自己在模板里套 AppLayout —— 这样登录页可以不带侧栏。
import { computed } from 'vue'
import { darkTheme } from 'naive-ui'
import type { GlobalTheme, GlobalThemeOverrides } from 'naive-ui'
import { isDark } from '@/stores/theme'

const theme = computed<GlobalTheme | null>(() => (isDark.value ? darkTheme : null))

/**
 * 让 naive 组件的「素色」与全局变量对齐。
 * 只覆盖极少数关键值，其余交给 naive 自己的 dark 主题——
 * 全量覆盖意味着替 naive 维护一整套调色板，不值得。
 */
const overrides = computed<GlobalThemeOverrides>(() => {
  if (!isDark.value) {
    return {
      common: {
        primaryColor: '#2080f0',
        primaryColorHover: '#4098fc',
        primaryColorPressed: '#1060c9'
      }
    }
  }
  return {
    common: {
      primaryColor: '#4c9dff',
      primaryColorHover: '#66aeff',
      primaryColorPressed: '#3d8be8',
      bodyColor: '#14161c',
      cardColor: '#1c1f27',
      modalColor: '#1c1f27',
      popoverColor: '#1e222b',
      inputColor: '#22262f',
      actionColor: '#22262f',
      tableColor: '#1c1f27',
      borderColor: '#2e3440',
      dividerColor: '#2e3440'
    }
  }
})
</script>

<template>
  <n-config-provider :theme="theme" :theme-overrides="overrides">
    <n-message-provider>
      <n-dialog-provider>
        <router-view />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
