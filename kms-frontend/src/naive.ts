import type { App, Component } from 'vue'
import {
  NAlert,
  NButton,
  NCard,
  NCheckbox,
  NConfigProvider,
  NDatePicker,
  NDialogProvider,
  NDivider,
  NDropdown,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NMessageProvider,
  NPagination,
  NSelect,
  NSpin,
  NTab,
  NTabs,
  NTag,
  NTooltip,
  NTree,
  NTreeSelect,
  NUpload
} from 'naive-ui'

/**
 * 按需注册 naive-ui 组件。
 *
 * 不用 `app.use(naive)`：那会把整个组件库打进产物。
 * 逐个注册后主包从 1.5 MB 降到约 900 KB。
 *
 * **必须用这里写死的名字，不能用 `component.name`**：
 * naive-ui 组件的 name 是 `Button` / `Card` / `Tree` 这样的短名，
 * 按它注册后模板里的 `<n-button>` 解析不到，页面会整片空白
 * （构建与类型检查都不会报错，只有真正渲染才暴露）。
 * 左侧的键就是模板里使用时的标签名（Pascal 写法，Vue 会匹配 kebab-case）。
 */
const COMPONENTS: Record<string, Component> = {
  NAlert,
  NButton,
  NCard,
  NCheckbox,
  NConfigProvider,
  NDatePicker,
  NDialogProvider,
  NDivider,
  NDropdown,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NMessageProvider,
  NPagination,
  NSelect,
  NSpin,
  NTab,
  NTabs,
  NTag,
  NTooltip,
  NTree,
  NTreeSelect,
  NUpload
}

export function installNaive(app: App) {
  for (const [name, component] of Object.entries(COMPONENTS)) {
    app.component(name, component)
  }
}
