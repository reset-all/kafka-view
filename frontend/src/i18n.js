// Simple i18n helper: provides Chinese (zh) and English (en) labels.
// By default this helper returns `zh (en)` to match existing UI style.

const messages = {
  brokers: { zh: '代理数', en: 'Brokers' },
  topics: { zh: '主题数', en: 'Topics' },
  partitions: { zh: '分区数', en: 'Partitions' },
  underReplicated: { zh: '未同步副本', en: 'Under Replicated' },
  missingReplicas: { zh: '缺失副本', en: 'Missing Replicas' },
  diskUsage: { zh: '磁盘使用', en: 'Disk Usage' },
  configs: { zh: '配置', en: 'Configs' },
  groups: { zh: '消费组', en: 'Groups' },
  producers: { zh: '生产者', en: 'Producers' },
  messages: { zh: '消息', en: 'Messages' },
    backfill: { zh: '回填', en: 'Backfill' },
  // Cluster / ClusterList
  name: { zh: '名称', en: 'Name' },
  bootstrapServers: { zh: 'Bootstrap 地址', en: 'Bootstrap Servers' },
  version: { zh: '版本', en: 'Version' },
  protocol: { zh: '协议', en: 'Protocol' },
  actions: { zh: '操作', en: 'Actions' },
  addCluster: { zh: '添加集群', en: 'Add Cluster' },
  editCluster: { zh: '编辑集群', en: 'Edit Cluster' },
  monitor: { zh: '查看详情', en: 'View Details' },
  confirm: { zh: '确定', en: 'Confirm' },
  cancel: { zh: '取消', en: 'Cancel' },
  delete: { zh: '删除', en: 'Delete' }
}

// current locale (not used for now - we always show both zh and en)
export let locale = 'zh'

export function setLocale(l) {
  locale = l
}

export function label(key) {
  const m = messages[key]
  if (!m) return key
  // Default UI shows Chinese with English in parentheses
  return `${m.zh} (${m.en})`
}

export function t(key) {
  const m = messages[key]
  if (!m) return key
  return locale === 'en' ? m.en : m.zh
}

export default { label, t, setLocale }
