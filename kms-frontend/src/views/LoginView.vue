<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/api/client'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const message = useMessage()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value.trim() || !password.value) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch (e) {
    // 后端的「用户名或密码错误」「账号已被禁用」都直接展示给用户
    message.error(describeError(e))
  } finally {
    loading.value = false
  }
}

function fill(name: string, pwd: string) {
  username.value = name
  password.value = pwd
}
</script>

<template>
  <div class="login-page">
    <n-card class="login-card" :bordered="false">
      <h1 class="title">知识管理系统</h1>
      <p class="subtitle">南京邮电大学 专业课程设计II · 题目1</p>

      <n-form @submit.prevent="submit">
        <n-form-item label="用户名" path="username">
          <n-input
            v-model:value="username"
            placeholder="请输入用户名"
            :input-props="{ autocomplete: 'username' }"
            @keyup.enter="submit"
          />
        </n-form-item>
        <n-form-item label="密码" path="password">
          <n-input
            v-model:value="password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
            :input-props="{ autocomplete: 'current-password' }"
            @keyup.enter="submit"
          />
        </n-form-item>
        <n-button type="primary" block :loading="loading" attr-type="submit" @click="submit">
          登录
        </n-button>
      </n-form>

      <!-- 课程设计项目，直接给出测试账号，省去演示时翻文档 -->
      <n-divider><span class="divider-text">测试账号</span></n-divider>
      <div class="accounts">
        <n-button size="small" quaternary @click="fill('admin', 'admin123')">
          管理员 admin / admin123
        </n-button>
        <n-button size="small" quaternary @click="fill('user', 'user123')">
          普通用户 user / user123
        </n-button>
      </div>

      <div class="back">
        <n-button text @click="router.push('/')">先随便看看（无需登录）</n-button>
      </div>
    </n-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--kms-login-bg);
}

.login-card {
  width: 100%;
  max-width: 380px;
  box-shadow: var(--kms-shadow-lg);
  border-radius: 10px;
}

.title {
  margin: 0 0 4px;
  font-size: 20px;
  text-align: center;
}

.subtitle {
  margin: 0 0 20px;
  text-align: center;
  color: var(--kms-text-secondary);
  font-size: 12px;
}

.divider-text {
  font-size: 12px;
  color: var(--kms-text-secondary);
}

.accounts {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.back {
  margin-top: 16px;
  text-align: center;
}
</style>
