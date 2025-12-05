<template>
  <div class="topic-list">
    <div class="toolbar">
      <el-button type="primary" @click="showCreateDialog">Create Topic</el-button>
      <el-button @click="showGlobalSettings">Settings</el-button>
      <el-button @click="fetchTopics">刷新本页</el-button>
      <el-input
        v-model="searchKeyword"
        placeholder="Search Topic"
        style="width: 200px; margin-left: 10px;"
        clearable
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">Search</el-button>
        </template>
      </el-input>
    </div>

    <el-table :data="topics" style="width: 100%" v-loading="loading" border stripe>
      <el-table-column prop="name" label="主题名称 (Topic Name)" min-width="250" align="left" show-overflow-tooltip />
      <el-table-column prop="partitionCount" label="分区数 (Partitions)" width="150" align="left" show-overflow-tooltip />
      <el-table-column prop="replicationFactor" label="副本数 (Replicas)" width="150" align="left" show-overflow-tooltip />
      <el-table-column label="节点 (Brokers)" width="180" align="left" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.brokerIds ? scope.row.brokerIds.join(', ') : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="messageCount" label="消息数 (Messages)" width="180" align="left" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.messageCount.toLocaleString() }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="consumerGroupCount" label="消费组 (Groups)" width="150" align="left" show-overflow-tooltip>
        <template #default="scope">
          <el-tag size="small" type="info">{{ scope.row.consumerGroupCount }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="偏移量范围 (Offset Range)" width="280" align="left" show-overflow-tooltip>
        <template #default="scope">
          <el-tag size="small" type="info">{{ scope.row.minOffset }}</el-tag>
          <span style="margin: 0 10px; color: #909399;">→</span>
          <el-tag size="small" type="success">{{ scope.row.maxOffset }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="近7天 (7d)" width="230" align="left">
        <template #default="scope">
          <div style="display: flex; align-items: center; justify-content: space-between; padding: 0 10px;">
            <Sparkline :data="scope.row.volumeData || []" :labels="dayLabels(7)" :width="'100%'" :height="30" :useEcharts="false" @open="() => openSparkline(scope.row)" />
            <el-button size="small" link type="primary" @click="() => openSparkline(scope.row)">View</el-button>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="回填 (Backfill)" width="120" align="left">
        <template #default="scope">
          <el-button
            size="small"
            type="primary"
            :loading="backfillLoading[scope.row.name]"
            :disabled="isBackfillDisabled(scope.row.name)"
            @click="() => handleBackfillClick(scope.row)">
            {{ backfillLoading[scope.row.name] ? '回填中' : '回填' }}
          </el-button>
        </template>
      </el-table-column>

      <el-table-column label="操作 (Actions)" min-width="520" fixed="right" align="left">
        <template #default="scope">
          <div class="actions-grid">
            <el-button class="action-btn" size="small" type="primary" plain @click="handleShowPartitions(scope.row)">{{ label('partitions') }}</el-button>
            <el-button class="action-btn" size="small" type="primary" plain @click="handleShowConfigs(scope.row)">{{ label('configs') }}</el-button>
            <el-button class="action-btn" size="small" type="primary" plain @click="handleShowGroups(scope.row)">{{ label('groups') }}</el-button>
            <el-button class="action-btn" size="small" type="primary" plain @click="handleShowProducers(scope.row)">{{ label('producers') }}</el-button>
            <el-button class="action-btn" size="small" type="primary" plain @click="handleShowMessages(scope.row)">{{ label('messages') }}</el-button>
            <el-button class="action-btn" size="small" type="danger" plain @click="handleDelete(scope.row)">{{ label('delete') }}</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-container">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="Create Topic" width="400px">
      <!-- ... existing dialog content ... -->
      <el-form :model="form" label-width="120px">
        <el-form-item label="Name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="Partitions">
          <el-input-number v-model="form.partitions" :min="1" />
        </el-form-item>
        <el-form-item label="Replicas">
          <el-input-number v-model="form.replicationFactor" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">Cancel</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">Create</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog v-model="globalSettingsDialogVisible" title="Global Settings" width="400px">
      <el-form :model="globalSettings" label-width="150px">
        <el-form-item label="Query Timeout (ms)">
          <el-input-number v-model="globalSettings.timeout" :min="100" :max="10000" :step="100" />
        </el-form-item>
        <el-form-item label="Retry Count">
          <el-input-number v-model="globalSettings.retryCount" :min="5" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="globalSettingsDialogVisible = false">Close</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog v-model="groupsDialogVisible" :title="'Consumer Groups - ' + currentTopicForGroups" width="90%" top="5vh">
      <div class="toolbar">
        <el-input
          v-model="groupsKeyword"
          placeholder="Search Group ID"
          style="width: 300px;"
          clearable
          @clear="fetchGroups"
          @keyup.enter="fetchGroups"
        >
          <template #append>
            <el-button @click="fetchGroups">Search</el-button>
          </template>
        </el-input>
      </div>
      
      <el-table :data="groupsData" v-loading="groupsLoading" height="600" border stripe>
        <el-table-column prop="groupId" label="消费组ID (Group ID)" min-width="250" />
        <el-table-column prop="state" label="状态 (State)" width="150">
          <template #default="scope">
             <el-tag :type="scope.row.state === 'Stable' ? 'success' : 'warning'">{{ scope.row.state }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="protocolType" label="协议 (Protocol)" width="120" />
        <el-table-column label="成员数 (Members)" width="100" align="center">
          <template #default="scope">
            <span>{{ scope.row.members ? scope.row.members.length : 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="堆积量 (Lag)" width="150" align="right">
          <template #default="scope">
            <span style="color: #f56c6c;">{{ scope.row.totalLag !== null ? scope.row.totalLag.toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已消费 (Consumed)" width="150" align="right">
          <template #default="scope">
            <span>{{ scope.row.topicMessageCount !== null ? scope.row.topicMessageCount.toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前偏移量 (Current Offset)" width="180" align="right">
          <template #default="scope">
            <span>{{ scope.row.currentOffset !== null ? scope.row.currentOffset.toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="起始偏移量 (Log Start)" width="180" align="right">
          <template #default="scope">
            <span>{{ scope.row.logStartOffset !== null ? scope.row.logStartOffset.toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="末尾偏移量 (Log End)" width="180" align="right">
          <template #default="scope">
            <span>{{ scope.row.logEndOffset !== null ? scope.row.logEndOffset.toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="groupsPage"
          v-model:page-size="groupsPageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="groupsTotal"
          @size-change="handleGroupsSizeChange"
          @current-change="handleGroupsCurrentChange"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="partitionsDialogVisible" :title="'Partitions - ' + currentTopicForPartitions" width="80%" top="5vh">
      <el-table :data="partitionsData" v-loading="partitionsLoading" height="600" border stripe>
        <el-table-column prop="partition" label="分区ID (ID)" width="100" align="center">
           <template #default="scope"><span>#{{ scope.row.partition }}</span></template>
        </el-table-column>
        <el-table-column prop="leader" label="Leader节点 (Leader)" min-width="200" />
        <el-table-column label="副本节点 (Replicas)" min-width="200">
          <template #default="scope">
            <span>{{ scope.row.replicas.join(', ') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="同步副本 (ISR)" min-width="200">
          <template #default="scope">
            <span style="color: #67c23a;">{{ scope.row.isr.join(', ') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="起始偏移量 (Start Offset)" width="150" align="right">
          <template #default="scope">
            <span>{{ scope.row.startOffset.toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="末尾偏移量 (End Offset)" width="150" align="right">
          <template #default="scope">
            <span>{{ scope.row.endOffset.toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="消息数 (Messages)" width="150" align="right">
          <template #default="scope">
            <span>{{ scope.row.messageCount.toLocaleString() }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="partitionsPage"
          v-model:page-size="partitionsPageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="partitionsTotal"
          @size-change="handlePartitionsSizeChange"
          @current-change="handlePartitionsCurrentChange"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="configsDialogVisible" :title="'Configs - ' + currentTopicForConfigs" width="70%" top="5vh">
      <el-table :data="configsData" v-loading="configsLoading" height="600" border stripe>
        <el-table-column prop="name" label="配置名 (Config Name)" min-width="250" />
        <el-table-column prop="value" label="配置值 (Value)" min-width="300">
           <template #default="scope">
             <span>{{ scope.row.value }}</span>
           </template>
        </el-table-column>
        <el-table-column label="默认值 (Default)" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isDefault" type="info" size="small">Default</el-tag>
            <el-tag v-else type="warning" size="small">Overridden</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="敏感 (Sensitive)" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isSensitive" type="danger" size="small">Yes</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作 (Actions)" width="100" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleEditConfig(scope.row)" :disabled="scope.row.isReadOnly">Edit</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="producersDialogVisible" :title="'Active Producers - ' + currentTopicForProducers" width="80%" top="5vh">
      <el-table :data="producersData" v-loading="producersLoading" height="600" border stripe>
        <el-table-column prop="partition" label="分区 (Partition)" width="100" align="center">
           <template #default="scope"><span>#{{ scope.row.partition }}</span></template>
        </el-table-column>
        <el-table-column prop="producerId" label="生产者ID (Producer ID)" min-width="150">
           <template #default="scope"><span>{{ scope.row.producerId }}</span></template>
        </el-table-column>
        <el-table-column prop="producerEpoch" label="纪元 (Epoch)" width="100" align="center">
           <template #default="scope"><span>{{ scope.row.producerEpoch }}</span></template>
        </el-table-column>
        <el-table-column prop="lastSequence" label="最后序列号 (Last Sequence)" width="150" align="right">
           <template #default="scope"><span>{{ scope.row.lastSequence }}</span></template>
        </el-table-column>
        <el-table-column label="最后时间戳 (Last Timestamp)" min-width="200">
          <template #default="scope">
            <span>{{ scope.row.lastTimestamp > 0 ? new Date(scope.row.lastTimestamp).toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="事务起始偏移量 (Txn Start Offset)" width="180" align="right">
          <template #default="scope">
            <span>{{ scope.row.currentTransactionStartOffset >= 0 ? scope.row.currentTransactionStartOffset : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="producersPage"
          v-model:page-size="producersPageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="producersTotal"
          @size-change="handleProducersSizeChange"
          @current-change="handleProducersCurrentChange"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="messagesDialogVisible" :title="'Messages - ' + currentTopicForMessages" width="90%" top="5vh">
      <el-tabs v-model="messagesActiveTab" @tab-click="handleMessagesTabClick">
        <el-tab-pane label="Search Messages" name="search">
          <div class="toolbar" style="flex-wrap: wrap; gap: 10px; margin-top: 10px;">
            <el-input v-model="messagesForm.key" placeholder="Key (Fuzzy)" style="width: 150px;" clearable />
            <el-input v-model="messagesForm.keyword" placeholder="Value Keyword (Fuzzy)" style="width: 150px;" clearable />
            <el-select v-model="messagesForm.partitions" multiple collapse-tags placeholder="Partitions (All)" style="width: 180px;" clearable>
              <el-option v-for="p in currentTopicPartitionCount" :key="p" :label="'Partition ' + p" :value="p" />
            </el-select>
            
            <el-date-picker
              v-model="messagesForm.timeRange"
              type="datetimerange"
              range-separator="To"
              start-placeholder="Start time"
              end-placeholder="End time"
              style="width: 320px;"
              :default-time="defaultTime"
            />
            
            <div style="display: flex; align-items: center; gap: 5px;">
               <el-input-number v-model="messagesForm.startOffset" :min="0" placeholder="Start Offset" :controls="false" style="width: 120px;" />
               <span>-</span>
               <el-input-number v-model="messagesForm.endOffset" :min="0" placeholder="End Offset" :controls="false" style="width: 120px;" />
            </div>

            <el-input-number v-model="messagesForm.limit" :min="1" :max="1000" placeholder="Limit" style="width: 100px;" controls-position="right" />

            <el-radio-group v-model="messagesScanDirection" size="small" @change="handleMessagesSearch">
              <el-radio-button label="desc">Newest First</el-radio-button>
              <el-radio-button label="asc">Oldest First</el-radio-button>
            </el-radio-group>

            <el-button type="primary" @click="handleMessagesSearch" :loading="messagesLoading">Search</el-button>
          </div>

          <div v-if="partitionBoundsList.length > 0" style="margin-bottom: 10px;">
            <el-button link type="primary" size="small" @click="showPartitionOffsets = !showPartitionOffsets" style="margin-bottom: 5px;">
              {{ showPartitionOffsets ? 'Hide' : 'Show' }} Partition Offsets Info
            </el-button>
            <el-table v-if="showPartitionOffsets" :data="partitionBoundsList" size="small" border stripe max-height="200">
                <el-table-column prop="partition" label="分区 (Partition)" width="100" align="center">
                   <template #default="scope">#{{ scope.row.partition }}</template>
                </el-table-column>
                <el-table-column prop="minOffset" label="最小偏移量 (Min Offset)" align="right">
                   <template #default="scope">{{ scope.row.minOffset.toLocaleString() }}</template>
                </el-table-column>
                <el-table-column prop="maxOffset" label="最大偏移量 (Max Offset)" align="right">
                   <template #default="scope">{{ scope.row.maxOffset.toLocaleString() }}</template>
                </el-table-column>
                <el-table-column label="消息总数 (Total Messages)" align="right">
                    <template #default="scope">{{ (scope.row.maxOffset - scope.row.minOffset).toLocaleString() }}</template>
                </el-table-column>
            </el-table>
          </div>
          
          <el-table :data="messagesData" v-loading="messagesLoading" height="550" border stripe @sort-change="handleMessagesSortChange" :default-sort="{ prop: 'timestamp', order: 'descending' }">
            <el-table-column prop="partition" label="分区 (Partition)" width="100" align="center" sortable="custom">
               <template #default="scope"><span>#{{ scope.row.partition }}</span></template>
            </el-table-column>
            <el-table-column prop="offset" label="偏移量 (Offset)" width="120" align="right" sortable="custom" />
            <el-table-column prop="timestamp" label="时间戳 (Timestamp)" width="200" sortable="custom">
              <template #default="scope">
                <span>{{ new Date(scope.row.timestamp).toLocaleString() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="key" label="键 (Key)" width="200" show-overflow-tooltip />
            <el-table-column label="值 (Value)" min-width="300">
              <template #default="scope">
                <div style="display: flex; align-items: center; justify-content: space-between;">
                  <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 10px;">{{ scope.row.value }}</span>
                  <el-button size="small" link type="primary" @click="handleShowMessageDetail(scope.row)">View</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="messagesPage"
              v-model:page-size="messagesPageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              :total="messagesTotal"
              @size-change="handleMessagesSizeChange"
              @current-change="handleMessagesCurrentChange"
            />
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="Send & History" name="send">
          <div style="display: flex; gap: 20px; height: 650px;">
            <div style="flex: 1; border-right: 1px solid #dcdfe6; padding-right: 20px;">
              <h3>Send Message</h3>
              <el-form :model="sendMessageForm" label-width="80px">
                <el-form-item label="Partition">
                  <el-select v-model="sendMessageForm.partition" placeholder="Auto (Round Robin)" clearable style="width: 100%">
                    <el-option v-for="p in currentTopicPartitionCount" :key="p" :label="'Partition ' + p" :value="p" />
                  </el-select>
                </el-form-item>
                <el-form-item label="Key">
                  <el-input v-model="sendMessageForm.key" placeholder="Optional Key" />
                </el-form-item>
                <el-form-item label="Value" required>
                  <el-input
                    v-model="sendMessageForm.value"
                    type="textarea"
                    :rows="15"
                    placeholder="Message Content (JSON, Text, etc.)"
                  />
                </el-form-item>
                <el-form-item label="Count">
                  <div style="display: flex; align-items: center; gap: 10px; width: 100%;">
                    <el-input-number v-model="sendMessageForm.count" :min="1" :max="200" style="width: 150px;" />
                    <div style="display: flex; gap: 5px; flex-wrap: wrap;">
                      <el-tag 
                        v-for="c in [20, 30, 50, 100, 150, 200]" 
                        :key="c" 
                        size="small" 
                        effect="plain" 
                        style="cursor: pointer;"
                        @click="sendMessageForm.count = c"
                      >
                        {{ c }}
                      </el-tag>
                    </div>
                  </div>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleSendMessage" :loading="sendingMessage" style="width: 100%;">Send Message</el-button>
                </el-form-item>
              </el-form>
            </div>
            
            <div style="flex: 1; display: flex; flex-direction: column;">
              <h3>Send History (Last 100)</h3>
              <el-table :data="historyData" v-loading="historyLoading" border stripe style="flex: 1; overflow: auto;">
                <el-table-column prop="createdAt" label="Time" width="160">
                   <template #default="scope">
                     <span>{{ new Date(scope.row.createdAt).toLocaleString() }}</span>
                   </template>
                </el-table-column>
                <el-table-column prop="partitionId" label="Partition" width="100" align="center">
                  <template #default="scope">
                    <span>{{ scope.row.partitionId !== null ? scope.row.partitionId : 'Auto' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="keyContent" label="Key" width="100" show-overflow-tooltip />
                <el-table-column label="Value" min-width="150">
                  <template #default="scope">
                    <div style="display: flex; align-items: center; justify-content: space-between;">
                      <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 10px;">{{ scope.row.valueContent }}</span>
                      <el-button size="small" link type="primary" @click="handleShowHistoryDetail(scope.row)">View</el-button>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="Action" width="80" align="center">
                  <template #default="scope">
                    <el-button size="small" type="primary" link @click="handleResend(scope.row)">Resend</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="pagination-container">
                <el-pagination
                  v-model:current-page="historyPage"
                  v-model:page-size="historyPageSize"
                  :page-sizes="[10, 20, 50]"
                  layout="total, prev, pager, next"
                  :total="historyTotal"
                  @size-change="handleHistorySizeChange"
                  @current-change="handleHistoryCurrentChange"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <el-dialog v-model="messageDetailDialogVisible" title="Message Details" width="800px" top="5vh">
      <el-descriptions border :column="2" style="margin-bottom: 20px;">
        <el-descriptions-item label="Topic">{{ currentTopicForMessages }}</el-descriptions-item>
        <el-descriptions-item label="Partition">#{{ currentMessageDetail.partition }}</el-descriptions-item>
        <el-descriptions-item label="Offset">{{ currentMessageDetail.offset }}</el-descriptions-item>
        <el-descriptions-item label="Timestamp">{{ new Date(currentMessageDetail.timestamp).toLocaleString() }}</el-descriptions-item>
        <el-descriptions-item label="Key" :span="2">{{ currentMessageDetail.key || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="messageDetailTab">
        <el-tab-pane label="Formatted (JSON)" name="json">
          <div style="margin-bottom: 10px; display: flex; align-items: center; gap: 10px;">
            <el-button size="small" @click="handleExpandAll">Expand All</el-button>
            <el-button size="small" @click="handleCollapseAll">Collapse All</el-button>
            <el-button size="small" type="primary" plain @click="handleCopyJson">Copy JSON</el-button>
            <div style="flex: 1;"></div>
            <el-input 
              v-model="jsonSearchKeyword" 
              placeholder="Search in JSON" 
              size="small" 
              style="width: 200px;" 
              clearable
              @keyup.enter="handleJsonSearch"
              @clear="highlightKeyword = ''"
            >
              <template #append>
                <el-button @click="handleJsonSearch">Search</el-button>
              </template>
            </el-input>
          </div>
          <div v-if="parsedJsonData !== null" style="max-height: 500px; overflow: auto; border: 1px solid #dcdfe6; padding: 10px; border-radius: 4px;">
            <json-viewer :data="parsedJsonData" :expand-signal="expandSignal" :highlight-text="highlightKeyword" />
          </div>
          <div v-else style="padding: 20px; text-align: center; color: #909399;">
            Not a valid JSON string
          </div>
        </el-tab-pane>
        <el-tab-pane label="Raw Text" name="raw">
          <el-input
            v-model="currentMessageDetail.value"
            type="textarea"
            :rows="20"
            readonly
          />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
    
    <!-- Sparkline enlarge drawer -->
    <el-drawer
      v-model="sparklineDialogVisible"
      :title="sparklineTopic ? 'Volume - ' + sparklineTopic.name : 'Volume'"
      direction="rtl"
      size="50%"
      append-to-body
    >
      <div style="display: flex; flex-direction: column; gap: 20px; height: 100%;">
        <div style="flex: 1; min-height: 300px;">
          <Sparkline v-if="sparklineDialogVisible" :data="sparklineData" :labels="dayLabels(sparklineDays)" :width="'100%'" :height="300" :useEcharts="true" :showAxes="true" />
        </div>
        <div style="flex: 1; overflow: auto; border-top: 1px solid #eee; padding-top: 20px;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px;">
             <h3>Data Points</h3>
             <div style="display: flex; align-items: center; gap: 10px;">
               <el-input-number v-model="sparklineDays" :min="1" :max="30" size="small" style="width: 100px;" @change="handleDaysChange" />
               <span>Days</span>
               <el-button type="primary" :loading="sparklineTopic && backfillLoading[sparklineTopic.name]" :disabled="sparklineTopic && isBackfillDisabled(sparklineTopic.name)" @click="handleDialogBackfill">手动回填 (Backfill)</el-button>
             </div>
          </div>
          <div style="margin-top: 12px;">
            <div v-if="sparklineData && sparklineData.length">
              <el-table :data="sparklineData.map((val, idx) => ({ date: dayLabels(sparklineDays)[idx], value: val }))" border stripe size="small">
                 <el-table-column prop="date" label="Date">
                    <template #default="scope">{{ scope.row.date ? scope.row.date.toLocaleDateString() : '-' }}</template>
                 </el-table-column>
                 <el-table-column prop="value" label="Volume">
                    <template #default="scope">{{ scope.row.value !== null && scope.row.value !== undefined ? scope.row.value.toLocaleString() : '-' }}</template>
                 </el-table-column>
              </el-table>
            </div>
            <div v-else style="color:#909399; padding:12px">No data</div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import { getTopics, createTopic, deleteTopic, getConsumerGroups, getTopicPartitions, getTopicConfigs, updateTopicConfigs, getTopicProducers, getTopicMessages, sendTopicMessage, getMessageHistory, getTopicsVolume, postBackfillTopics } from '../api'
import { label } from '../i18n'
import Sparkline from './Sparkline.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import JsonViewer from './JsonViewer.vue'

const props = defineProps({
  clusterId: {
    type: Number,
    required: true
  }
})

const topics = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const searchKeyword = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)

// Global Settings
const globalSettingsDialogVisible = ref(false)
const globalSettings = reactive({
  timeout: 1000,
  retryCount: 10
})

// Groups Dialog State
const groupsDialogVisible = ref(false)
const groupsLoading = ref(false)
const groupsData = ref([])
const groupsTotal = ref(0)
const groupsPage = ref(1)
const groupsPageSize = ref(10)
const groupsKeyword = ref('')
const currentTopicForGroups = ref('')

// Partitions Dialog State
const partitionsDialogVisible = ref(false)
const partitionsLoading = ref(false)
const partitionsData = ref([])
const partitionsTotal = ref(0)
const partitionsPage = ref(1)
const partitionsPageSize = ref(10)
const currentTopicForPartitions = ref('')

// Configs Dialog State
const configsDialogVisible = ref(false)
const configsLoading = ref(false)
const configsData = ref([])
const currentTopicForConfigs = ref('')

// Producers Dialog State
const producersDialogVisible = ref(false)
const producersLoading = ref(false)
const producersData = ref([])
const producersTotal = ref(0)
const producersPage = ref(1)
const producersPageSize = ref(10)
const currentTopicForProducers = ref('')

// Messages Dialog State
const messagesDialogVisible = ref(false)
const messagesActiveTab = ref('search')
const messagesLoading = ref(false)
const messagesData = ref([])
const messagesTotal = ref(0)
const messagesPage = ref(1)
const messagesPageSize = ref(20)
const messagesSortField = ref('timestamp')
const messagesSortOrder = ref('desc')
const messagesScanDirection = ref('desc')
const messagesPartitionBounds = ref({}) // { pid: { minOffset, maxOffset } }
const currentTopicForMessages = ref('')
const currentTopicPartitionCount = ref([]) // Array of partition IDs [0, 1, 2...]
const messagesForm = reactive({
  key: '',
  keyword: '',
  partitions: [],
  timeRange: [],
  startOffset: undefined,
  endOffset: undefined,
  limit: 100
})
const defaultTime = [
  new Date(2000, 1, 1, 0, 0, 0),
  new Date(2000, 2, 1, 23, 59, 59),
]

// Send Message State
const sendingMessage = ref(false)
const sendMessageForm = reactive({
  partition: undefined,
  key: '',
  value: '',
  count: 1
})

// History State
const historyLoading = ref(false)
const historyData = ref([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historyPageSize = ref(10)

// Message Detail Dialog State
const messageDetailDialogVisible = ref(false)
const currentMessageDetail = ref({})
const messageDetailTab = ref('json')
const expandSignal = ref(0)
const showPartitionOffsets = ref(false)
const jsonSearchKeyword = ref('')
const highlightKeyword = ref('')

const partitionBoundsList = computed(() => {
  return messagesPartitionBounds.value ? Object.values(messagesPartitionBounds.value).sort((a, b) => a.partition - b.partition) : []
})

const backfillLoading = reactive({})
const backfillCooldown = reactive({}) // timestamp in ms until which button is disabled

const COOLDOWN_KEY = 'kafka_view_backfill_cooldowns'

// Volume fix audit log (record topics that were auto-reversed to match labels)
const VOLUME_FIX_LOG_KEY = 'kafka_view_volume_fix_log'
const volumeFixLog = ref([]) // array of { topic, at }

function saveVolumeFixLog() {
  try { window.localStorage.setItem(VOLUME_FIX_LOG_KEY, JSON.stringify(volumeFixLog.value)) } catch (e) {}
}

function loadVolumeFixLog() {
  try {
    const raw = window.localStorage.getItem(VOLUME_FIX_LOG_KEY)
    if (!raw) return
    const obj = JSON.parse(raw)
    if (Array.isArray(obj)) volumeFixLog.value = obj
  } catch (e) {}
}

function recordVolumeFix(topic) {
  try {
    const entry = { topic, at: Date.now() }
    volumeFixLog.value.push(entry)
    saveVolumeFixLog()
    console.info(`[TopicList] auto-reversed volumes for topic=${topic}`)
  } catch (e) {}
}

// Heuristic to detect if a series is reversed compared to labels (labels expected oldest->newest)
function shouldReverseSeries(data, labels) {
  if (!Array.isArray(data) || !Array.isArray(labels) || data.length !== labels.length) return false
  const parsed = labels.map(l => { try { return new Date(l) } catch (e) { return null } })
  if (!parsed[0] || !parsed[parsed.length - 1]) return false
  if (parsed[0].getTime() >= parsed[parsed.length - 1].getTime()) return false

  const len = data.length
  const firstNonNull = data.findIndex(v => v !== null && v !== undefined)
  let lastNonNull = -1
  for (let i = data.length - 1; i >= 0; i--) if (data[i] !== null && data[i] !== undefined) { lastNonNull = i; break }
  if (firstNonNull === -1 || lastNonNull === -1) return false

  // If most non-null values are clustered at the start of array while labels indicate end is newest,
  // likely the series is reversed.
  if (lastNonNull <= Math.floor(len * 0.2)) return true

  if (firstNonNull >= Math.ceil(len * 0.8) && parsed[parsed.length - 1].getTime() >= Date.now() - 48 * 3600 * 1000) return true

  return false
}

function saveCooldownsToStorage() {
  try {
    window.localStorage.setItem(COOLDOWN_KEY, JSON.stringify(backfillCooldown))
  } catch (e) {}
}

function loadCooldownsFromStorage() {
  try {
    const raw = window.localStorage.getItem(COOLDOWN_KEY)
    if (!raw) return
    const obj = JSON.parse(raw)
    const now = Date.now()
    for (const k of Object.keys(obj)) {
      const t = Number(obj[k])
      if (!t || t <= now) continue
      backfillCooldown[k] = t
      // schedule clear
      const delay = t - now
      setTimeout(() => { delete backfillCooldown[k]; saveCooldownsToStorage() }, delay)
    }
  } catch (e) {}
}

function setCooldown(topicName, minutes = 10) {
  const until = Date.now() + minutes * 60 * 1000
  backfillCooldown[topicName] = until
  saveCooldownsToStorage()
  setTimeout(() => { delete backfillCooldown[topicName]; saveCooldownsToStorage() }, minutes * 60 * 1000)
}

const sparklineDialogVisible = ref(false)
const sparklineTopic = ref(null)
const sparklineData = ref([])
const sparklineDays = ref(7)

function isBackfillDisabled(topicName) {
  const coolUntil = backfillCooldown[topicName]
  if (backfillLoading[topicName]) return true
  if (!coolUntil) return false
  return Date.now() < coolUntil
}

const openSparkline = (row) => {
  sparklineTopic.value = row
  sparklineData.value = Array.isArray(row.volumeData) ? row.volumeData : []
  sparklineDays.value = 7
  sparklineDialogVisible.value = true
}

const openPopover = (row) => {
  // set current topic for popover and data, then open the popover for that row
  sparklineTopic.value = row
  sparklineData.value = Array.isArray(row.volumeData) ? row.volumeData : []
  sparklineDays.value = 7
  // show popover
  // close other popovers first
  if (topics.value && topics.value.length) {
    for (const t of topics.value) {
      if (t !== row) t._popoverVisible = false
    }
  }
  row._popoverVisible = true
}

const handleDialogBackfillForRow = async (row) => {
  // helper used by popover button: ensure sparklineTopic is set
  sparklineTopic.value = row
  sparklineData.value = Array.isArray(row.volumeData) ? row.volumeData : []
  await handleDialogBackfill()
}

function dayLabels(days) {
  const labels = []
  const now = new Date()
  // oldest -> newest (same order as API)
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i)
    labels.push(d)
  }
  return labels
}

const handleBackfillClick = async (row, days = 7) => {
  const name = row.name
  if (isBackfillDisabled(name)) {
    ElMessage.info('Backfill is disabled for a short period after a recent run')
    return
  }
  backfillLoading[name] = true
  try {
    await postBackfillTopics(props.clusterId, [name], days)
    ElMessage.success('Backfill started for ' + name)
    // set 10-minute cooldown (persisted)
    setCooldown(name, 10)
    // refresh this topic volumes
    try {
      const batch = await getTopicsVolume(props.clusterId, [name], days)
      // If we are in the dialog (sparklineTopic matches), update sparklineData too
      if (sparklineTopic.value && sparklineTopic.value.name === name) {
         let arr = Array.isArray(batch[name]) ? batch[name] : []
         // auto-detect reversed order
         try {
            if (shouldReverseSeries(arr, dayLabels(days))) {
              arr = Array.from(arr).reverse()
            }
         } catch (e) {}
         sparklineData.value = arr
      }
      // Update row data if it's the default 7 days, or maybe just update it anyway if we want to reflect it in the table?
      // The table always shows 7 days. If we backfill 30 days, the last 7 days should still be valid.
      // But getTopicsVolume returns exactly 'days' points.
      // So if we backfilled 30 days, we should probably re-fetch 7 days for the table row to be correct.
      if (days !== 7) {
         const batch7 = await getTopicsVolume(props.clusterId, [name], 7)
         row.volumeData = Array.isArray(batch7[name]) ? batch7[name] : []
      } else {
         row.volumeData = Array.isArray(batch[name]) ? batch[name] : []
      }
    } catch (e) {}
  } catch (e) {
    ElMessage.error('Backfill failed: ' + (e?.message || 'unknown'))
  } finally {
    backfillLoading[name] = false
  }
}

const handleDialogBackfill = async () => {
  if (!sparklineTopic.value) return
  await handleBackfillClick(sparklineTopic.value, sparklineDays.value)
}

const handleDaysChange = async () => {
  if (!sparklineTopic.value) return
  try {
    const batch = await getTopicsVolume(props.clusterId, [sparklineTopic.value.name], sparklineDays.value)
    let arr = Array.isArray(batch[sparklineTopic.value.name]) ? batch[sparklineTopic.value.name] : []
    // auto-detect reversed order
    try {
      if (shouldReverseSeries(arr, dayLabels(sparklineDays.value))) {
        arr = Array.from(arr).reverse()
      }
    } catch (e) {}
    sparklineData.value = arr
  } catch (e) {
    sparklineData.value = []
  }
}

const parsedJsonData = computed(() => {
  if (!currentMessageDetail.value.value) return null
  try {
    return JSON.parse(currentMessageDetail.value.value)
  } catch (e) {
    return null
  }
})

const handleExpandAll = () => {
  expandSignal.value = Math.abs(expandSignal.value) + 1
}

const handleCollapseAll = () => {
  expandSignal.value = -(Math.abs(expandSignal.value) + 1)
}

const handleCopyJson = () => {
  if (parsedJsonData.value) {
    navigator.clipboard.writeText(JSON.stringify(parsedJsonData.value, null, 2))
    ElMessage.success('Copied to clipboard')
  }
}

const searchJson = (data, keyword) => {
  if (!data) return false
  if (typeof data === 'string') return data.toLowerCase().includes(keyword.toLowerCase())
  if (typeof data === 'number') return String(data).includes(keyword)
  if (Array.isArray(data)) return data.some(item => searchJson(item, keyword))
  if (typeof data === 'object') {
    return Object.keys(data).some(key => 
      key.toLowerCase().includes(keyword.toLowerCase()) || searchJson(data[key], keyword)
    )
  }
  return false
}

const handleJsonSearch = () => {
  if (!jsonSearchKeyword.value) {
    highlightKeyword.value = ''
    return
  }
  const found = searchJson(parsedJsonData.value, jsonSearchKeyword.value)
  if (found) {
    highlightKeyword.value = jsonSearchKeyword.value
  } else {
    highlightKeyword.value = ''
    ElMessage.warning('No match found')
  }
}

const form = reactive({
  name: '',
  partitions: 1,
  replicationFactor: 1
})

const fetchTopics = async () => {
  loading.value = true
  try {
    const res = await getTopics(props.clusterId, currentPage.value, pageSize.value, searchKeyword.value)
    topics.value = res.list
    total.value = res.total

    // Fetch per-topic 7-day volume data in a single batch request (best-effort)
    try {
      const topicNames = topics.value.map(t => t.name)
      const batch = await getTopicsVolume(props.clusterId, topicNames, 7)
      // batch is map: topic -> [numbers]
      const labels = dayLabels(7)
      for (const t of topics.value) {
        let arr = Array.isArray(batch[t.name]) ? batch[t.name] : []
        // auto-detect reversed order and fix centrally
        try {
          if (shouldReverseSeries(arr, labels)) {
            arr = Array.from(arr).reverse()
            recordVolumeFix(t.name)
          }
        } catch (e) {}
        t.volumeData = arr
        t.backfill = false
        t._popoverVisible = false
      }
    } catch (e) {
      for (const t of topics.value) {
        t.volumeData = []
        t.backfill = false
        t._popoverVisible = false
      }
    }
  } finally {
    loading.value = false
  }
}

const handleBackfillToggle = async (row, val) => {
  // Only perform backfill when turning on
  if (!val) return
  backfillLoading[row.name] = true
  try {
    await postBackfillTopics(props.clusterId, [row.name], 7)
    ElMessage.success('Backfill started for ' + row.name)

    // refresh volume for this topic (best-effort)
    try {
      const batch = await getTopicsVolume(props.clusterId, [row.name], 7)
      row.volumeData = Array.isArray(batch[row.name]) ? batch[row.name] : []
    } catch (e) {
      // ignore
    }
  } catch (e) {
    ElMessage.error('Backfill failed: ' + (e?.message || 'unknown'))
    // revert switch
    row.backfill = false
  } finally {
    backfillLoading[row.name] = false
  }
}

const fetchGroups = async () => {
  if (!currentTopicForGroups.value) return
  groupsLoading.value = true
  try {
    const res = await getConsumerGroups(
      props.clusterId, 
      groupsPage.value, 
      groupsPageSize.value, 
      groupsKeyword.value, 
      currentTopicForGroups.value
    )
    groupsData.value = res.list
    groupsTotal.value = res.total
  } finally {
    groupsLoading.value = false
  }
}

const fetchPartitions = async () => {
  if (!currentTopicForPartitions.value) return
  partitionsLoading.value = true
  try {
    const res = await getTopicPartitions(props.clusterId, currentTopicForPartitions.value, partitionsPage.value, partitionsPageSize.value)
    partitionsData.value = res.list
    partitionsTotal.value = res.total
  } finally {
    partitionsLoading.value = false
  }
}

const fetchConfigs = async () => {
  if (!currentTopicForConfigs.value) return
  configsLoading.value = true
  try {
    const res = await getTopicConfigs(props.clusterId, currentTopicForConfigs.value)
    configsData.value = res
  } finally {
    configsLoading.value = false
  }
}

const fetchProducers = async () => {
  if (!currentTopicForProducers.value) return
  producersLoading.value = true
  try {
    const res = await getTopicProducers(props.clusterId, currentTopicForProducers.value, producersPage.value, producersPageSize.value)
    producersData.value = res.list
    producersTotal.value = res.total
  } finally {
    producersLoading.value = false
  }
}

const fetchMessages = async () => {
  if (!currentTopicForMessages.value) return
  messagesLoading.value = true
  try {
    const params = {
      key: messagesForm.key,
      keyword: messagesForm.keyword,
      partitions: messagesForm.partitions,
      startTime: messagesForm.timeRange && messagesForm.timeRange[0] ? messagesForm.timeRange[0].getTime() : undefined,
      endTime: messagesForm.timeRange && messagesForm.timeRange[1] ? messagesForm.timeRange[1].getTime() : undefined,
      startOffset: messagesForm.startOffset,
      endOffset: messagesForm.endOffset,
      limit: messagesForm.limit,
      page: messagesPage.value,
      pageSize: messagesPageSize.value,
      sortField: messagesSortField.value,
      sortOrder: messagesSortOrder.value,
      scanDirection: messagesScanDirection.value,
      timeout: globalSettings.timeout,
      retryCount: globalSettings.retryCount
    }
    const res = await getTopicMessages(props.clusterId, currentTopicForMessages.value, params)
    messagesData.value = res.list
    messagesTotal.value = res.total
    messagesPartitionBounds.value = res.partitionOffsets || {}
  } finally {
    messagesLoading.value = false
  }
}

const handleMessagesSortChange = ({ prop, order }) => {
  messagesSortField.value = prop
  messagesSortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  fetchMessages()
}

const handleMessagesSearch = () => {
  messagesPage.value = 1
  fetchMessages()
}

const handleMessagesSizeChange = (val) => {
  messagesPageSize.value = val
  fetchMessages()
}

const handleMessagesCurrentChange = (val) => {
  messagesPage.value = val
  fetchMessages()
}

const handleEditConfig = (row) => {
  ElMessageBox.prompt('Please input new value for ' + row.name, 'Edit Config', {
    confirmButtonText: 'OK',
    cancelButtonText: 'Cancel',
    inputValue: row.value,
  }).then(async ({ value }) => {
    try {
      const configMap = {}
      configMap[row.name] = value
      await updateTopicConfigs(props.clusterId, currentTopicForConfigs.value, configMap)
      ElMessage.success('Config updated')
      fetchConfigs()
    } catch (e) {
      // handled
    }
  }).catch(() => {
    // cancel
  })
}

const handleShowGroups = (row) => {
  currentTopicForGroups.value = row.name
  groupsKeyword.value = ''
  groupsPage.value = 1
  groupsDialogVisible.value = true
  fetchGroups()
}

const handleShowPartitions = (row) => {
  currentTopicForPartitions.value = row.name
  partitionsPage.value = 1
  partitionsDialogVisible.value = true
  fetchPartitions()
}

const handleShowConfigs = (row) => {
  currentTopicForConfigs.value = row.name
  configsDialogVisible.value = true
  fetchConfigs()
}

const handleShowProducers = (row) => {
  currentTopicForProducers.value = row.name
  producersPage.value = 1
  producersDialogVisible.value = true
  fetchProducers()
}

const fetchHistory = async () => {
  if (!currentTopicForMessages.value) return
  historyLoading.value = true
  try {
    const res = await getMessageHistory(props.clusterId, currentTopicForMessages.value, historyPage.value, historyPageSize.value)
    historyData.value = res.list
    historyTotal.value = res.total
  } finally {
    historyLoading.value = false
  }
}

const handleHistorySizeChange = (val) => {
  historyPageSize.value = val
  fetchHistory()
}

const handleHistoryCurrentChange = (val) => {
  historyPage.value = val
  fetchHistory()
}

const handleResend = (row) => {
  sendMessageForm.partition = row.partitionId
  sendMessageForm.key = row.keyContent
  sendMessageForm.value = row.valueContent
  ElMessage.info('Message content loaded into form')
}

const handleShowMessages = (row) => {
  currentTopicForMessages.value = row.name
  // Generate partition list for select
  currentTopicPartitionCount.value = Array.from({length: row.partitionCount}, (_, i) => i)
  
  messagesForm.key = ''
  messagesForm.keyword = ''
  messagesForm.partitions = []
  messagesForm.timeRange = []
  messagesForm.startOffset = undefined
  messagesForm.endOffset = undefined
  messagesForm.limit = 100
  messagesScanDirection.value = 'desc'
  messagesSortOrder.value = 'desc'
  messagesPartitionBounds.value = {}
  messagesPage.value = 1
  messagesData.value = []
  messagesTotal.value = 0
  
  // Reset Send Form
  sendMessageForm.partition = undefined
  sendMessageForm.key = ''
  sendMessageForm.value = ''
  sendMessageForm.count = 1
  
  // Reset History
  historyPage.value = 1
  historyData.value = []
  historyTotal.value = 0
  
  messagesActiveTab.value = 'search'
  messagesDialogVisible.value = true
  fetchMessages()
}

const handleSendMessage = async () => {
  if (!sendMessageForm.value) {
    ElMessage.warning('Message value is required')
    return
  }
  sendingMessage.value = true
  try {
    await sendTopicMessage(props.clusterId, currentTopicForMessages.value, sendMessageForm)
    ElMessage.success('Message sent successfully')
    // Refresh history
    fetchHistory()
  } catch (e) {
    // handled
  } finally {
    sendingMessage.value = false
  }
}

const handleMessagesTabClick = (tab) => {
  if (tab.props.name === 'send') {
    fetchHistory()
  }
}

const handleShowMessageDetail = (row) => {
  currentMessageDetail.value = row
  messageDetailTab.value = 'json'
  expandSignal.value = 0
  jsonSearchKeyword.value = ''
  highlightKeyword.value = ''
  // Check if it's JSON, if not switch to raw
  try {
    JSON.parse(row.value)
  } catch (e) {
    messageDetailTab.value = 'raw'
  }
  messageDetailDialogVisible.value = true
}

const handleShowHistoryDetail = (row) => {
  const detail = {
    partition: row.partitionId !== null ? row.partitionId : 'Auto',
    offset: '-',
    timestamp: row.createdAt,
    key: row.keyContent,
    value: row.valueContent
  }
  
  currentMessageDetail.value = detail
  messageDetailTab.value = 'json'
  expandSignal.value = 0
  jsonSearchKeyword.value = ''
  highlightKeyword.value = ''
  
  try {
    JSON.parse(detail.value)
  } catch (e) {
    messageDetailTab.value = 'raw'
  }
  messageDetailDialogVisible.value = true
}

const handleGroupsSizeChange = (val) => {
  groupsPageSize.value = val
  fetchGroups()
}

const handleGroupsCurrentChange = (val) => {
  groupsPage.value = val
  fetchGroups()
}

const handlePartitionsSizeChange = (val) => {
  partitionsPageSize.value = val
  fetchPartitions()
}

const handlePartitionsCurrentChange = (val) => {
  partitionsPage.value = val
  fetchPartitions()
}

const handleProducersSizeChange = (val) => {
  producersPageSize.value = val
  fetchProducers()
}

const handleProducersCurrentChange = (val) => {
  producersPage.value = val
  fetchProducers()
}

const handleSearch = () => {
  currentPage.value = 1
  fetchTopics()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  fetchTopics()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchTopics()
}

const showCreateDialog = () => {
  form.name = ''
  form.partitions = 1
  form.replicationFactor = 1
  dialogVisible.value = true
}

const showGlobalSettings = () => {
  globalSettingsDialogVisible.value = true
}

const handleSubmit = async () => {
  if (!form.name) return
  submitting.value = true
  try {
    await createTopic(props.clusterId, form)
    ElMessage.success('Topic created')
    dialogVisible.value = false
    fetchTopics()
  } catch (e) {
    // handled by interceptor
  } finally {
    submitting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`Are you sure to delete topic "${row.name}"?`, 'Warning', {
    confirmButtonText: 'Continue',
    cancelButtonText: 'Cancel',
    type: 'warning'
  }).then(() => {
    ElMessageBox.confirm(
      `<div style="color: red; font-weight: bold; font-size: 16px;">
         This action CANNOT be undone!<br/>
         Confirm to delete topic: ${row.name}
       </div>`,
      'Final Confirmation',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'error',
        dangerouslyUseHTMLString: true,
        confirmButtonClass: 'el-button--danger'
      }
    ).then(async () => {
      await deleteTopic(props.clusterId, row.name)
      ElMessage.success('Deleted')
      fetchTopics()
    }).catch(() => {})
  }).catch(() => {})
}

onMounted(async () => {
  loadCooldownsFromStorage()
  loadVolumeFixLog()
  await fetchTopics()
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 15px;
  display: flex;
  align-items: center;
}
.pagination-container {
  margin-top: 15px;
  display: flex;
  justify-content: flex-end;
}
:deep(.el-table th.el-table__cell) {
  font-weight: 900 !important;
  color: #000000 !important;
  font-size: 14px;
}

/* popper class for sparkline popovers to avoid clipping and ensure proper z-index */
:deep(.kafka-sparkline-popper) {
  z-index: 9999 !important;
  overflow: visible !important;
}

/* Actions grid: make buttons uniform size and align into two rows */
.actions-grid {
  display: grid;
  /* use three columns for actions to keep consistent layout and avoid overlap */
  grid-template-columns: repeat(3, minmax(140px, 1fr));
  grid-auto-rows: auto;
  gap: 12px; /* slightly larger gap to keep spacing */
  width: 100%;
  align-items: center;
  grid-auto-flow: row;
}

:deep(.actions-grid .action-btn) {
  width: 120px;
  min-width: 0;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  box-sizing: border-box;
  margin: 0 auto !important; /* center within grid cell */
}

/* Ensure inner text uses normal alignment (centered by button) */
:deep(.actions-grid .action-btn .el-button__text) {
  display: block !important;
  width: 100% !important;
  text-align: center !important;
}

/* All buttons are left-aligned; no per-column override needed */

/* smaller screens: allow smaller min width so grid can flow to more columns */
@media (max-width: 600px) {
  .actions-grid {
    grid-template-columns: repeat(auto-fit, minmax(90px, 1fr));
    grid-auto-rows: auto;
  }
}

@media (max-width: 600px) {
  .actions-grid {
    grid-template-columns: repeat(auto-fit, minmax(90px, 1fr));
    gap: 8px;
  }
  :deep(.actions-grid .action-btn) {
    width: 100px;
    height: 34px;
    padding: 0 8px;
    margin: 0 auto !important;
  }
}
</style>
