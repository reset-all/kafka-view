<template>
  <div class="cluster-list">
    <div class="toolbar">
      <el-button type="primary" @click="showAddDialog">Add Cluster</el-button>
    </div>
    
    <div class="table-wrapper">
      <el-table :data="clusters" style="width: 100%;" v-loading="loading" border stripe>
        <el-table-column prop="name" :label="label('name')" min-width="180">
        <template #default="scope">
            <span style="font-weight: 600;">{{ scope.row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="bootstrapServers" :label="label('bootstrapServers')" min-width="240" show-overflow-tooltip />
      <el-table-column prop="kafkaVersion" :label="label('version')" width="120" align="center">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.versionSupported === false ? 'warning' : 'info'">
            {{ scope.row.versionSupported === false ? '低版本' : (scope.row.kafkaVersion || '-') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="securityProtocol" :label="label('protocol')" width="220" align="center">
        <template #default="scope">
          <el-tag size="small" :type="(!scope.row.securityProtocol || scope.row.securityProtocol === '') ? 'warning' : (scope.row.securityProtocol === 'PLAINTEXT' ? 'warning' : 'success')">
            {{ (scope.row.securityProtocol && scope.row.securityProtocol !== '') ? scope.row.securityProtocol : 'None' }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column :label="label('actions')" min-width="195" align="center">
        <template #default="scope">
          <div style="display: flex; gap: 8px; flex-wrap: nowrap; justify-content: center; align-items: center;">
            <el-button size="small" type="primary" plain @click="handleSelect(scope.row)">{{ label('monitor') }}</el-button>
            <el-button size="small" type="primary" plain @click="handleEdit(scope.row)">{{ label('editCluster') }}</el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(scope.row)">{{ label('delete') }}</el-button>
          </div>
        </template>
      </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? label('editCluster') : label('addCluster')" width="500px" destroy-on-close append-to-body @closed="onDialogClosed">
      <el-form :model="form" label-width="140px">
        <el-form-item label="Name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="Bootstrap Servers">
          <el-input v-model="form.bootstrapServers" placeholder="localhost:9092" />
        </el-form-item>
        <el-form-item label="Security Protocol">
          <el-select v-model="form.securityProtocol" placeholder="Select">
            <el-option label="None" value="" />
            <el-option label="PLAINTEXT" value="PLAINTEXT" />
            <el-option label="SASL_PLAINTEXT" value="SASL_PLAINTEXT" />
            <el-option label="SASL_SSL" value="SASL_SSL" />
          </el-select>
        </el-form-item>
        <template v-if="form.securityProtocol !== 'PLAINTEXT' && form.securityProtocol !== ''">
           <el-form-item label="SASL Mechanism">
            <el-select v-model="form.saslMechanism">
              <el-option label="PLAIN" value="PLAIN" />
              <el-option label="SCRAM-SHA-256" value="SCRAM-SHA-256" />
            </el-select>
          </el-form-item>
          <el-form-item label="Username">
            <el-input v-model="form.username" />
          </el-form-item>
          <el-form-item label="Password">
            <el-input v-model="form.password" type="password" show-password />
          </el-form-item>
        </template>
        <!-- JMX Port removed from UI -->
        <el-form-item label="Timeout (ms)">
            <el-input-number v-model="form.timeout" :min="1000" :step="1000" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
          <span class="dialog-footer">
          <el-button @click="handleCancel">{{ label('cancel') }}</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ label('confirm') }}</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { getClusters, addCluster, updateCluster, deleteCluster } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { label } from '../i18n'

const emit = defineEmits(['select'])

const clusters = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)

const form = reactive({
  id: null,
  name: '',
  bootstrapServers: '',
  kafkaVersion: '',
  securityProtocol: '',
  saslMechanism: 'PLAIN',
  username: '',
  password: '',
  timeout: 15000,
  versionSupported: true
})

const fetchClusters = async () => {
  loading.value = true
  try {
    clusters.value = await getClusters()
  } catch (error) {
    // Error is handled in API interceptor, but we catch here to prevent uncaught promise
    console.log('Failed to fetch clusters:', error.message)
  } finally {
    loading.value = false
  }
}

const showAddDialog = () => {
  // ensure any stale overlays are removed before opening
  removeOverlays()

  Object.assign(form, {
    id: null,
    name: '',
    bootstrapServers: '',
    kafkaVersion: '',
    securityProtocol: '',
    saslMechanism: 'PLAIN',
    username: '',
    password: '',
    // jmxPort intentionally omitted from UI
    timeout: 15000,
    versionSupported: true
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  // remove any leftover overlays before opening the edit dialog
  removeOverlays()

  Object.assign(form, {
    id: row.id,
    name: row.name,
    bootstrapServers: row.bootstrapServers,
    kafkaVersion: row.kafkaVersion,
    securityProtocol: row.securityProtocol || '',
    saslMechanism: row.saslMechanism || 'PLAIN',
    username: row.username,
    password: row.password,
    timeout: row.timeout || 15000,
    versionSupported: row.versionSupported !== false
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    if (form.id) {
      await updateCluster(form)
      ElMessage.success('Cluster updated successfully')
    } else {
      await addCluster(form)
      ElMessage.success('Cluster added successfully')
    }
    dialogVisible.value = false
    onDialogClosed()
    fetchClusters()
  } catch (e) {
    // Error handled in api interceptor
  } finally {
    submitting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm('Are you sure to delete this cluster?', 'Warning', {
    confirmButtonText: 'OK',
    cancelButtonText: 'Cancel',
    type: 'warning'
  }).then(async () => {
    await deleteCluster(row.id)
    ElMessage.success('Deleted')
    fetchClusters()
  })
}

const handleSelect = (row) => {
  // ensure overlays cleared before switching to Monitor view
  removeOverlays()
  emit('select', row)
}

onMounted(fetchClusters)

const removeOverlays = () => {
  try {
    // Safer cleanup: avoid deleting overlay DOM nodes (Element Plus manages them internally).
    // Instead restore body state so UI becomes interactive again.
    document.body.classList.remove('el-popup-parent--hidden')
    document.body.style.overflow = ''
    document.body.style.pointerEvents = ''

    // Remove any dialog wrappers that are fully hidden (cleanup only, do not touch active wrappers)
    const wrappers = document.querySelectorAll('.el-dialog__wrapper')
    wrappers.forEach(w => {
      try {
        const style = window.getComputedStyle(w)
        if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) {
          w.parentNode && w.parentNode.removeChild(w)
        }
      } catch (innerErr) {
        // ignore individual wrapper errors
      }
    })
  } catch (err) {
    if (window && window.console && window.console.debug) window.console.debug('removeOverlays error', err)
  }

  // Run a second lightweight pass to ensure body state restored
  setTimeout(() => {
    try {
      document.body.classList.remove('el-popup-parent--hidden')
      document.body.style.overflow = ''
      document.body.style.pointerEvents = ''
    } catch (e) {
      if (window && window.console && window.console.debug) window.console.debug('second pass removeOverlays error', e)
    }
  }, 120)
}

const onDialogClosed = () => {
  // Ensure dialog state is false and remove overlays (twice to be safe)
  dialogVisible.value = false
  removeOverlays()
  // Final cleanup after a short delay in case Element Plus re-adds overlay
  setTimeout(() => removeOverlays(), 120)
}

const handleCancel = () => {
  // Properly close dialog and cleanup overlays
  dialogVisible.value = false
  onDialogClosed()
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 20px;
}
/* allow horizontal scrolling on narrow viewports so columns don't get squashed */
.table-wrapper {
  overflow-x: auto;
}
/* center all table headers and cell content inside this component */
.table-wrapper .el-table th .cell,
.table-wrapper .el-table td .cell {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}
</style>
