<script setup lang="ts">
// 只提供全局的 naive-ui provider 与路由出口。
// 业务页面自己在模板里套 AppLayout —— 这样登录页可以不带侧栏。
</script>

<template>
  <n-message-provider>
    <n-dialog-provider>
      <router-view />
    </n-dialog-provider>
  </n-message-provider>
</template>
