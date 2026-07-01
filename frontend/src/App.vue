<template>
  <div
    class="app-shell"
    :class="{ 'dragging-file': isDraggingFile }"
    @dragenter.prevent="handlePageDragEnter"
    @dragover.prevent="handlePageDragOver"
    @dragleave.prevent="handlePageDragLeave"
    @drop.prevent="handlePageFileDrop"
  >
    <div v-if="isDraggingFile" class="drop-overlay">
      <div>
        <strong>松开上传法律资料</strong>
        <span>支持 PDF / TXT，Word 请先另存为 PDF</span>
      </div>
    </div>
    <aside class="sidebar">
      <div class="brand-block">
        <p class="eyebrow">LexScope</p>
        <h1>法律智能问答</h1>
        <p class="brand-sub">
          上传法律资料，输入你的问题，系统将基于文档内容生成有依据的法律分析。
        </p>
      </div>

      <div class="session-search-block">
        <el-input
          v-model="sessionSearch"
          size="small"
          placeholder="搜索记录标题或 id"
          clearable
        />
      </div>

      <button class="new-chat-btn" type="button" @click="createAndSwitchSession">+ 新建问答</button>

      <section class="session-panel">
        <div class="section-head">
          <p class="section-label">我的记录</p>
          <span class="section-meta">{{ filteredSessions.length }}/{{ sessionCount }}</span>
        </div>
        <div class="session-list">
          <div
            v-for="session in filteredSessions"
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === activeSessionId }"
            role="button"
            tabindex="0"
            @click="switchSession(session.id)"
            @keydown.enter.prevent="switchSession(session.id)"
          >
            <div class="session-content">
              <div class="session-title-row">
                <p class="session-title">{{ session.title }}</p>
                <el-tag v-if="session.pinned" size="small" type="success" effect="plain"
                  >置顶</el-tag
                >
                <el-tag v-if="session.archived" size="small" type="info" effect="plain"
                  >归档</el-tag
                >
              </div>
              <p class="session-meta-row">
                {{ formatSessionTime(session.updatedAt) }}
              </p>
            </div>
            <div class="session-actions">
              <button type="button" @click.stop="toggleSessionPin(session.id)">
                {{ session.pinned ? '取消置顶' : '置顶' }}
              </button>
              <button type="button" @click.stop="toggleSessionArchive(session.id)">
                {{ session.archived ? '取消归档' : '归档' }}
              </button>
              <button type="button" class="danger" @click.stop="removeSession(session.id)">
                删除
              </button>
            </div>
          </div>
          <div v-if="filteredSessions.length === 0" class="session-empty">没有匹配记录</div>
        </div>
      </section>

      <div class="sidebar-footer">
        <div class="sidebar-usage-card">
          本月用量 ${{ (costSummary?.monthCostUsd ?? 0).toFixed(4) }}
        </div>
        <button class="settings-link" type="button" @click="settingsVisible = true">
          设置
        </button>
      </div>
    </aside>

    <main class="workspace">
      <header class="workspace-head">
        <div v-if="activeView === 'chat'">
          <p class="workspace-kicker">智能问答</p>
          <h2>{{ activeSession?.title || '新对话' }}</h2>
          <p class="workspace-sub">可继续提问、补充资料，或整理成法律分析报告。</p>
        </div>
        <div v-else>
          <p class="workspace-kicker">开发者工具</p>
          <h2>{{ selectedEvalDataset?.name || '效果评测' }}</h2>
          <p class="workspace-sub">用于测试检索命中、引用覆盖和回答可靠性，普通使用无需打开。</p>
        </div>
        <div v-if="activeView === 'chat'" class="head-actions">
          <el-switch v-model="darkMode" inline-prompt active-text="深色" inactive-text="浅色" />
        </div>
        <div v-else class="head-actions">
          <el-button
            size="small"
            :loading="evalLoading"
            :disabled="!canUseRemoteSync"
            @click="loadEvalDatasets"
            >刷新</el-button
          >
          <el-button
            size="small"
            type="primary"
            :loading="evalRunning"
            :disabled="!canUseRemoteSync || !evalSelectedDatasetId"
            @click="runSelectedEvalDataset"
            >运行评测</el-button
          >
          <el-button
            size="small"
            :disabled="!canUseRemoteSync || !evalCurrentRun"
            :loading="evalReportExporting"
            @click="downloadEvalReport"
            >导出报告</el-button
          >
          <el-button
            size="small"
            :disabled="!canUseRemoteSync || !evalCurrentRun"
            @click="markCurrentEvalRunBaseline"
            >设为参考结果</el-button
          >
          <el-button size="small" @click="activateView('chat')">返回问答</el-button>
          <el-button size="small" @click="settingsVisible = true">设置</el-button>
        </div>
      </header>

      <el-drawer v-model="settingsVisible" title="设置" size="380px">
        <div class="settings-drawer">
          <section class="settings-card">
            <p class="section-label">基础设置</p>
            <el-form label-position="top" size="small">
              <el-form-item label="模型 API Key">
                <el-input
                  v-model="apiKeyInput"
                  placeholder="已默认使用本地模型 API Key"
                  show-password
                  type="password"
                />
              </el-form-item>
            </el-form>
            <div class="auth-buttons">
              <el-button type="primary" :loading="authLoading" @click="handleLogin"
                >保存设置</el-button
              >
              <el-button @click="clearAuth">恢复默认</el-button>
            </div>
          </section>

          <details class="ops-panel">
            <summary><span>数据管理</span></summary>
            <div class="ops-body">
              <div class="developer-tools">
                <div class="developer-button-pair">
                  <el-button
                    size="small"
                    :loading="cloudSyncing"
                    :disabled="!canUseRemoteSync"
                    @click="loadSessionsFromCloud"
                    >从云端读取</el-button
                  >
                  <el-button
                    size="small"
                    type="primary"
                    :loading="cloudSyncing"
                    :disabled="!canUseRemoteSync"
                    @click="syncActiveSessionToCloud"
                    >保存至云端</el-button
                  >
                </div>
              </div>
            </div>
          </details>

          <details class="ops-panel">
            <summary><span>高级设置</span></summary>
            <div class="ops-body">
              <div class="developer-tools">
                <label class="developer-field">
                  <span>使用范围</span>
                  <el-input
                    v-model="tenantInput"
                    size="small"
                    class="developer-control"
                    placeholder="public"
                  />
                </label>
                <label class="developer-field">
                  <span>当前分组</span>
                  <el-select v-model="activeWorkspaceId" size="small" class="developer-control">
                    <el-option
                      v-for="workspace in workspaceOptions"
                      :key="workspace"
                      :label="workspaceLabel(workspace)"
                      :value="workspace"
                    />
                  </el-select>
                </label>
                <label class="developer-field">
                  <span>新分组</span>
                  <el-input
                    v-model="workspaceDraft"
                    size="small"
                    class="developer-control"
                    placeholder="输入新分组名称"
                    @keydown.enter.prevent="createWorkspace"
                  />
                </label>
                <el-button class="developer-full-button" size="small" @click="createWorkspace"
                  >创建并切换</el-button
                >
                <label class="developer-field">
                  <span>记录筛选</span>
                  <el-select v-model="workspaceFilter" size="small" class="developer-control">
                    <el-option label="全部分组" value="all" />
                    <el-option
                      v-for="workspace in workspaceOptions"
                      :key="workspace"
                      :label="workspaceLabel(workspace)"
                      :value="workspace"
                    />
                  </el-select>
                </label>
                <div class="developer-field developer-switch-row">
                  <span>归档记录</span>
                  <el-switch
                    v-model="showArchivedSessions"
                    size="small"
                    inline-prompt
                    active-text="显示"
                    inactive-text="隐藏"
                  />
                </div>
                <div class="history-settings">
                  <div class="branch-panel-head">
                    <p class="branch-panel-title">
                      历史版本
                      <span>{{ activeSession?.branches.length ?? 0 }} 个</span>
                    </p>
                    <div class="branch-head-actions">
                      <button type="button" @click="forkFromCurrent">另存为新版本</button>
                      <button
                        type="button"
                        :disabled="!activeBranch?.parentBranchId"
                        @click="compareWithParent"
                      >
                        对比上一版
                      </button>
                      <button
                        type="button"
                        :disabled="!activeBranch?.parentBranchId"
                        @click="mergeIntoParent"
                      >
                        采用当前版本
                      </button>
                    </div>
                  </div>

                  <div class="branch-list">
                    <div
                      v-for="node in branchTreeItems"
                      :key="node.branch.id"
                      class="branch-item"
                      :class="{ active: node.branch.id === activeBranch?.id }"
                      :style="{ paddingLeft: `${12 + node.depth * 14}px` }"
                      role="button"
                      tabindex="0"
                      @click="switchBranch(node.branch.id)"
                      @keydown.enter.prevent="switchBranch(node.branch.id)"
                    >
                      <span
                        class="branch-line"
                        :style="{ opacity: node.depth > 0 ? 1 : 0 }"
                      ></span>
                      <div class="branch-content">
                        <p>{{ node.branch.title }}</p>
                        <small>{{ formatTime(node.branch.updatedAt) }}</small>
                      </div>
                    </div>
                    <div v-if="branchTreeItems.length === 0" class="session-empty">
                      暂无历史版本
                    </div>
                  </div>
                </div>
                <el-button
                  class="developer-full-button"
                  size="small"
                  @click="openDeveloperEvaluation"
                  >打开效果评测</el-button
                >
              </div>
            </div>
          </details>

        </div>
      </el-drawer>

      <template v-if="activeView === 'chat'">
        <section
          ref="messageContainer"
          class="messages"
          @scroll="onMessageScroll"
          @click="handleMarkdownClick"
        >
          <div v-if="hydrating" class="hydration-skeleton">
            <div class="skeleton-line lg"></div>
            <div class="skeleton-line"></div>
            <div class="skeleton-line short"></div>
            <div class="skeleton-bubble"></div>
            <div class="skeleton-bubble alt"></div>
          </div>

          <template v-else>
            <div v-if="isEmptyConversation" class="welcome-block">
              <h3>开始法律智能问答</h3>
              <p>上传合同、判决书或法规材料，直接提问即可获得分析。</p>
              <div class="welcome-core">
                <div class="welcome-file-badge" aria-hidden="true">
                  <el-icon><DocumentIcon /></el-icon>
                </div>
                <button
                  class="welcome-upload-button"
                  type="button"
                  :disabled="uploadingLegalFile"
                  @click="openWelcomeFilePicker"
                >
                  <el-icon><UploadIcon /></el-icon>
                  <span>{{ uploadingLegalFile ? '上传中...' : '上传法律资料' }}</span>
                </button>
                <input
                  ref="welcomeFileInput"
                  class="welcome-file-input"
                  type="file"
                  accept=".pdf,.txt,.doc,.docx,application/pdf,text/plain"
                  @change="handleWelcomeFileSelected"
                />
                <small>支持 PDF / Word / TXT</small>
              </div>
              <div class="welcome-divider"><span>或者直接输入问题开始</span></div>
              <div class="welcome-prompts">
                <button
                  v-for="sample in welcomePrompts"
                  :key="sample"
                  type="button"
                  @click="prompt = sample"
                >
                  <span>{{ sample }}</span>
                </button>
              </div>
            </div>

            <div class="virtual-spacer" :style="{ height: `${virtualTopSpacer}px` }"></div>

            <article
              v-for="entry in virtualMessages"
              :key="entry.item.id"
              :ref="(el) => setMessageRowRef(entry.item.id, el as HTMLElement | null)"
              :data-msg-id="entry.item.id"
              class="message-row"
              :class="[entry.item.role, entry.item.state || 'done']"
            >
              <div class="bubble-wrap">
                <div class="bubble-meta">
                  <span>{{ entry.item.role === 'user' ? '我' : '法律助手' }}</span>
                  <span>{{ formatTime(entry.item.createdAt) }}</span>
                  <span v-if="entry.item.state === 'streaming'" class="status-dot">生成中</span>
                  <span v-if="entry.item.state === 'pending'" class="status-dot">思考中</span>
                </div>

                <div class="bubble">
                  <template v-if="entry.item.role === 'assistant'">
                    <div
                      v-if="entry.item.state === 'pending' && !entry.item.content"
                      class="assistant-skeleton"
                    >
                      <div></div>
                      <div></div>
                      <div></div>
                    </div>
                    <div v-else>
                      <!-- eslint-disable-next-line vue/no-v-html -->
                      <div class="markdown" v-html="renderMarkdown(entry.item.content)"></div>
                      <div v-if="citationCards(entry.item).length" class="citation-panel">
                        <p class="citation-title">参考来源</p>
                        <div class="citation-card-list">
                          <button
                            v-for="card in citationCards(entry.item)"
                            :key="`${entry.item.id}-${card.index}-${card.fileName}-${card.pageNumber ?? ''}`"
                            type="button"
                            class="citation-card"
                            @click="openCitation(card)"
                          >
                            <span class="citation-card-number">[{{ card.index }}]</span>
                            <span class="citation-card-main">
                              <strong>{{ card.fileName }}</strong>
                              <small v-if="card.pageNumber">第 {{ card.pageNumber }} 页</small>
                              <span v-if="card.snippet">{{ card.snippet }}</span>
                            </span>
                          </button>
                        </div>
                      </div>
                      <div
                        v-if="shouldShowFollowUpSuggestions(entry.item)"
                        class="follow-up-suggestions"
                      >
                        <p>我理解你可能还想进一步了解：</p>
                        <ol>
                          <li
                            v-for="question in followUpSuggestionsFor(entry.item, entry.index)"
                            :key="`${entry.item.id}-${question}`"
                          >
                            {{ question }}
                          </li>
                        </ol>
                        <p>需要我继续帮你分析其中一个问题吗？</p>
                      </div>
                      <details
                        v-if="traceSteps.length && entry.index === virtualMessages.length - 1"
                        class="trace-timeline-panel"
                      >
                        <summary class="trace-timeline-header">
                          <span class="trace-timeline-title">查看处理过程</span>
                          <span class="trace-timeline-meta"
                            >{{ traceSteps.length }} 步 · {{ traceDurationMs }}ms</span
                          >
                        </summary>
                        <div class="trace-timeline">
                          <div
                            v-for="(ts, tsIdx) in traceSteps"
                            :key="`trace-${tsIdx}`"
                            class="trace-timeline-step"
                            :class="[
                              `trace-action-${ts.action}`,
                              { 'trace-last': tsIdx === traceSteps.length - 1 },
                            ]"
                          >
                            <div class="trace-timeline-rail">
                              <div class="trace-node"></div>
                              <div
                                v-if="tsIdx < traceSteps.length - 1"
                                class="trace-connector"
                              ></div>
                            </div>
                            <div class="trace-timeline-content">
                              <div class="trace-step-header">
                                <span class="trace-step-label">步骤 {{ ts.step }}</span>
                                <span class="trace-action-badge" :class="`badge-${ts.action}`">{{
                                  ts.action
                                }}</span>
                              </div>
                              <div v-if="ts.thought" class="trace-thought-block">
                                <span class="trace-field-label">思路</span>
                                <p>{{ ts.thought }}</p>
                              </div>
                              <div
                                v-if="ts.actionInput && Object.keys(ts.actionInput).length"
                                class="trace-input-block"
                              >
                                <span class="trace-field-label">输入</span>
                                <pre>{{ JSON.stringify(ts.actionInput, null, 2) }}</pre>
                              </div>
                              <details v-if="ts.observation" class="trace-obs-block">
                                <summary>
                                  <span class="trace-field-label">结果</span>
                                </summary>
                                <div class="trace-obs-content">
                                  <pre>{{
                                    typeof ts.observation === 'string'
                                      ? ts.observation
                                      : JSON.stringify(ts.observation, null, 2)
                                  }}</pre>
                                </div>
                              </details>
                            </div>
                          </div>
                        </div>
                      </details>
                    </div>
                  </template>

                  <template v-else>
                    <div v-if="editingMessageId === entry.item.id" class="edit-box">
                      <el-input
                        v-model="editingMessageDraft"
                        type="textarea"
                        :autosize="{ minRows: 6, maxRows: 14 }"
                        resize="vertical"
                      />
                      <div class="edit-actions">
                        <el-button size="small" @click="cancelEditMessage">取消</el-button>
                        <el-button
                          size="small"
                          type="primary"
                          :disabled="!editingMessageDraft.trim() || sending"
                          @click="submitEditAndResend(entry.index, entry.item.id)"
                        >
                          保存为新版本
                        </el-button>
                      </div>
                    </div>
                    <p v-else class="plain">{{ entry.item.content }}</p>
                  </template>
                </div>

                <div class="message-actions">
                  <button type="button" @click="copyMessage(entry.item.content)">复制</button>
                  <button
                    v-if="entry.item.role === 'assistant'"
                    type="button"
                    @click="regenerateFrom(entry.index)"
                  >
                    重新生成
                  </button>
                  <button
                    v-if="entry.item.role === 'assistant'"
                    type="button"
                    :disabled="
                      Boolean(answerFeedbackMap[entry.item.id]) ||
                      Boolean(answerFeedbackLoading[entry.item.id])
                    "
                    @click="rateAnswer(entry.index, entry.item, 5)"
                  >
                    有帮助
                  </button>
                  <button
                    v-if="entry.item.role === 'assistant'"
                    type="button"
                    :disabled="
                      Boolean(answerFeedbackMap[entry.item.id]) ||
                      Boolean(answerFeedbackLoading[entry.item.id])
                    "
                    @click="rateAnswer(entry.index, entry.item, 1)"
                  >
                    待改进
                  </button>
                  <button
                    v-if="entry.item.role === 'user'"
                    type="button"
                    @click="startEditMessage(entry.item)"
                  >
                    编辑
                  </button>
                </div>
              </div>
            </article>

            <div class="virtual-spacer" :style="{ height: `${virtualBottomSpacer}px` }"></div>

            <div v-if="sending && isStreamingResponse" class="thinking">
              {{ streamStatusLabel }} · {{ streamStatusDetail || '处理中...' }}
            </div>
          </template>
        </section>

        <footer class="composer-shell">
          <div class="composer">
            <p v-if="!canUseRemoteSync" class="model-service-tip">
              当前未完成登录或服务配置，请前往设置检查。
            </p>
            <el-input
              v-model="prompt"
              class="composer-input"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 10 }"
              resize="none"
              placeholder="输入你的问题，Enter 发送，Shift + Enter 换行"
              @keydown.enter.exact.prevent="send"
            />
            <div class="composer-footer">
              <div class="composer-left">
                <div class="composer-controls">
                  <el-select
                    v-model="modelProfile"
                    size="small"
                    class="composer-quality-select"
                    placement="top"
                    aria-label="回答质量"
                  >
                    <el-option
                      v-for="option in modelProfileOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                  <button
                    type="button"
                    class="composer-toggle"
                    :class="{ active: streaming }"
                    @click="streaming = !streaming"
                  >
                    <span aria-hidden="true"></span>
                    {{ streaming ? '实时输出' : '单次输出' }}
                  </button>
                </div>
              </div>
              <div class="composer-actions">
                <el-button :disabled="!sending" @click="stopGenerating">停止</el-button>
                <el-button
                  type="primary"
                  :loading="sending"
                  :disabled="!prompt.trim() || sending"
                  @click="send"
                  >发送</el-button
                >
              </div>
            </div>
          </div>
        </footer>
      </template>

      <section v-else class="evaluation-page">
        <aside class="eval-side-panel">
          <div class="eval-panel-head">
            <div>
              <p class="section-label">检查集</p>
              <strong>{{ evalDatasets.length }}</strong>
            </div>
          </div>

          <div class="eval-dataset-list">
            <button
              v-for="dataset in evalDatasets"
              :key="dataset.datasetId"
              type="button"
              :class="{ active: dataset.datasetId === evalSelectedDatasetId }"
              @click="selectEvalDataset(dataset.datasetId)"
            >
              <span>{{ dataset.name }}</span>
              <small>{{ dataset.caseCount }} 条 · {{ shortId(dataset.datasetId) }}</small>
            </button>
            <div v-if="!evalDatasets.length" class="session-empty">暂无检查集</div>
          </div>

          <div class="eval-create-panel">
            <p class="section-label">创建检查集</p>
            <el-input v-model="evalDatasetName" size="small" placeholder="检查集名称" />
            <el-input v-model="evalDatasetDescription" size="small" placeholder="说明" />
            <el-input
              v-model="evalDatasetJson"
              class="eval-json-input"
              type="textarea"
              :rows="11"
              resize="none"
              spellcheck="false"
            />
            <el-button
              type="primary"
              :loading="evalCreating"
              :disabled="!canUseRemoteSync || !evalDatasetName.trim() || !evalDatasetJson.trim()"
              @click="createEvalDatasetFromJson"
              >创建检查集</el-button
            >
          </div>
        </aside>

        <section class="eval-main-panel">
          <div class="eval-score-strip">
            <div v-for="metric in evalMetricCards" :key="metric.key" class="eval-metric-card">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.current }}</strong>
              <small :class="metric.deltaClass">{{ metric.delta }}</small>
            </div>
          </div>

          <div class="eval-run-grid">
            <div class="eval-run-summary">
              <p class="section-label">参考结果</p>
              <strong>{{ evalBaselineRun?.runId || '暂无' }}</strong>
              <span>{{ formatRunScore(evalBaselineRun?.metrics.runScore) }}</span>
            </div>
            <div class="eval-run-summary current">
              <p class="section-label">当前结果</p>
              <strong>{{ evalCurrentRun?.runId || '暂无' }}</strong>
              <span>{{ formatRunScore(evalCurrentRun?.metrics.runScore) }}</span>
            </div>
          </div>

          <el-table
            :data="evalCurrentRun?.results ?? []"
            class="eval-result-table"
            height="100%"
            empty-text="暂无评测结果"
          >
            <el-table-column prop="caseId" label="问题编号" min-width="120" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="得分" width="110">
              <template #default="{ row }">{{ formatPercent(row.score) }}</template>
            </el-table-column>
            <el-table-column label="引用覆盖" width="120">
              <template #default="{ row }">{{ formatPercent(row.citationCoverage) }}</template>
            </el-table-column>
            <el-table-column label="耗时" width="120">
              <template #default="{ row }">{{ row.latencyMs }}ms</template>
            </el-table-column>
            <el-table-column prop="question" label="问题" min-width="280" show-overflow-tooltip />
          </el-table>
        </section>
      </section>
    </main>

    <el-dialog
      v-model="citationPreviewVisible"
      class="citation-preview-dialog"
      title="参考来源"
      width="820px"
      @closed="closeCitationPreview"
    >
      <div class="citation-preview">
        <div v-if="citationPreviewCard" class="citation-preview-meta">
          <span class="citation-card-number">[{{ citationPreviewCard.index }}]</span>
          <strong>{{ citationPreviewCard.fileName }}</strong>
          <small v-if="citationPreviewCard.pageNumber">第 {{ citationPreviewCard.pageNumber }} 页</small>
        </div>
        <p v-if="citationPreviewCard?.snippet" class="citation-preview-snippet">
          {{ citationPreviewCard.snippet }}
        </p>
        <div v-if="citationPreviewLoading" class="citation-preview-state">正在加载 PDF 预览...</div>
        <div v-else-if="citationPreviewError" class="citation-preview-state error">
          {{ citationPreviewError }}
        </div>
        <iframe
          v-else-if="citationPreviewPdfUrl"
          class="citation-preview-frame"
          :src="citationPreviewPdfUrl"
          title="PDF 来源预览"
        ></iframe>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import DOMPurify from 'dompurify';
import hljs from 'highlight.js/lib/core';
import bashLang from 'highlight.js/lib/languages/bash';
import javaLang from 'highlight.js/lib/languages/java';
import javascriptLang from 'highlight.js/lib/languages/javascript';
import jsonLang from 'highlight.js/lib/languages/json';
import markdownLang from 'highlight.js/lib/languages/markdown';
import pythonLang from 'highlight.js/lib/languages/python';
import sqlLang from 'highlight.js/lib/languages/sql';
import typescriptLang from 'highlight.js/lib/languages/typescript';
import xmlLang from 'highlight.js/lib/languages/xml';
import yamlLang from 'highlight.js/lib/languages/yaml';
import { ElMessage } from 'element-plus';
import { Document as DocumentIcon, Upload as UploadIcon } from '@element-plus/icons-vue';
import { marked } from 'marked';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  compareSessionBranches,
  createEvalDataset,
  exportEvalRunReport,
  fetchPdfFile,
  getEvalComparison,
  getTenantCostSummary,
  listEvalDatasets,
  markEvalRunBaseline,
  listSessionStates,
  mergeSessionBranches,
  reactChat,
  saveSessionState,
  streamReactChat,
  submitAnswerFeedback,
  triggerEvalRun,
  uploadLegalFile,
} from './api/client';
import type {
  CitationLike,
  EvalCaseCreate,
  EvalComparison,
  EvalDataset,
  EvalMetricSummary,
  EvalRun,
  ReactChatResponse,
  ReactErrorEvent,
  ReactTokenEvent,
  ReactTraceStep,
  SessionState,
  TenantCostSummary,
} from './types/react';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: number;
  citations?: CitationLike[];
  evidence?: string[];
  state?: 'pending' | 'streaming' | 'done' | 'error' | 'stopped';
}

interface CitationCard {
  index: number;
  fileName: string;
  pageNumber?: number | null;
  snippet?: string;
  debug?: Record<string, unknown>;
}

interface SessionBranch {
  id: string;
  title: string;
  parentBranchId: string | null;
  parentMessageId: string | null;
  updatedAt: number;
  messages: ChatMessage[];
  traceSteps: ReactTraceStep[];
}

interface SessionRecord {
  id: string;
  title: string;
  updatedAt: number;
  modelProfile: string;
  streaming: boolean;
  pinned: boolean;
  archived: boolean;
  workspaceId: string;
  activeBranchId: string;
  branches: SessionBranch[];
}

interface BranchTreeItem {
  branch: SessionBranch;
  depth: number;
}

interface MessageMetric {
  item: ChatMessage;
  index: number;
  offset: number;
  height: number;
}

type StreamPhase = 'idle' | 'thinking' | 'tool' | 'streaming' | 'done' | 'error' | 'stopped';
type ConsoleView = 'chat' | 'evaluation';

interface EvalMetricCard {
  key: keyof EvalMetricSummary;
  label: string;
  current: string;
  delta: string;
  deltaClass: string;
}

const STORAGE_KEY = 'lexscope-agent-react-console-v1';
const LEGACY_STORAGE_KEY = 'lexscope-agent-react-console';
const DEFAULT_SYSTEM_MESSAGE = '请上传法律资料，或直接输入你想分析的问题。';
const DEFAULT_WORKSPACE = 'default';
const DEFAULT_SERVICE_KEY = 'dev-admin-key-2026';
const ESTIMATED_ROW_HEIGHT = 156;
const OVERSCAN_COUNT = 8;
const AUTO_CLOUD_SYNC_DELAY_MS = 1600;
const AUTO_CLOUD_SYNC_ERROR_INTERVAL_MS = 60000;
const modelProfileOptions = [
  { label: '省钱模式', value: 'economy' },
  { label: '均衡模式', value: 'balanced' },
  { label: '高质量模式', value: 'quality' },
  { label: '自动选择', value: 'ab_auto' },
  { label: '高质量优先', value: 'quality_first' },
  { label: '成本优先', value: 'cost_first' },
];
const DEFAULT_EVAL_DATASET = [
  {
    caseId: 'rag_001',
    category: 'rag_recall',
    chatId: 'eval-rag-a',
    question: '根据资料，合同纠纷中一方扩大损失时应承担什么后果？',
    expectedKeywords: ['扩大损失', '赔偿', '合同', '引用'],
    forbiddenKeywords: ['我不知道', '无法回答'],
  },
  {
    caseId: 'rag_002',
    category: 'rag_precision',
    chatId: 'eval-rag-b',
    question: '请总结这份资料的基本事实、处理结果和关键要点。',
    expectedKeywords: ['事实', '结果', '要点', '引用'],
    forbiddenKeywords: ['与问题无关', '编造'],
  },
  {
    caseId: 'rag_003',
    category: 'citation_coverage',
    chatId: 'eval-rag-b',
    question: '回答时列出引用来源，并提炼可复用的风险提示。',
    expectedKeywords: ['引用', '风险', '依据', '提示'],
  },
];

hljs.registerLanguage('bash', bashLang);
hljs.registerLanguage('java', javaLang);
hljs.registerLanguage('javascript', javascriptLang);
hljs.registerLanguage('json', jsonLang);
hljs.registerLanguage('markdown', markdownLang);
hljs.registerLanguage('python', pythonLang);
hljs.registerLanguage('sql', sqlLang);
hljs.registerLanguage('typescript', typescriptLang);
hljs.registerLanguage('xml', xmlLang);
hljs.registerLanguage('yaml', yamlLang);

function safeParse(raw: string | null): Record<string, unknown> {
  if (!raw) {
    return {};
  }
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function toBase64(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function fromBase64(value: string): string {
  const binary = atob(value);
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

const renderer = new marked.Renderer();
renderer.code = ((token: { text: string; lang?: string }) => {
  const rawCode = token.text ?? '';
  const lang = token.lang?.trim().toLowerCase().split(/\s+/)[0] ?? 'plaintext';
  const language = hljs.getLanguage(lang) ? lang : 'plaintext';
  const highlighted =
    language === 'plaintext'
      ? escapeHtml(rawCode)
      : hljs.highlight(rawCode, { language, ignoreIllegals: true }).value;

  const lines = highlighted.split('\n');
  const numbered = lines
    .map((line, index) => {
      const content = line || '&nbsp;';
      return `<span class="code-line"><span class="line-no">${index + 1}</span><span class="line-content">${content}</span></span>`;
    })
    .join('');

  const payload = escapeHtml(toBase64(rawCode));

  return `<div class="code-block"><div class="code-toolbar"><span class="code-lang">${language}</span><button class="copy-code-btn" type="button" data-code="${payload}">复制代码</button></div><pre><code class="hljs language-${language}">${numbered}</code></pre></div>`;
}) as typeof renderer.code;

marked.use({
  gfm: true,
  breaks: true,
  renderer,
});

function createChatId(): string {
  const suffix = Math.random().toString(36).slice(2, 8);
  return `react-${Date.now()}-${suffix}`;
}

function createBranchId(): string {
  const suffix = Math.random().toString(36).slice(2, 8);
  return `branch-${Date.now()}-${suffix}`;
}

function createMessage(role: ChatMessage['role'], content: string): ChatMessage {
  return {
    id: `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role,
    content,
    createdAt: Date.now(),
    state: 'done',
  };
}

function normalizeMessage(raw: unknown): ChatMessage {
  const candidate = (raw ?? {}) as Partial<ChatMessage>;
  const role = candidate.role === 'assistant' ? 'assistant' : 'user';
  return {
    id: candidate.id || `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role,
    content:
      typeof candidate.content === 'string'
        ? role === 'assistant'
          ? cleanAssistantContent(candidate.content)
          : candidate.content
        : '',
    createdAt: typeof candidate.createdAt === 'number' ? candidate.createdAt : Date.now(),
    citations: Array.isArray(candidate.citations)
      ? candidate.citations.map((item) => normalizeCitationLike(item)).filter(isCitationLike)
      : [],
    evidence: Array.isArray(candidate.evidence)
      ? candidate.evidence.map((item) => String(item).trim()).filter(Boolean)
      : [],
    state: candidate.state || 'done',
  };
}

function deriveTitle(text: string): string {
  const clean = text.trim().replace(/\s+/g, ' ');
  if (!clean) {
    return '新对话';
  }
  return clean.length > 28 ? `${clean.slice(0, 28)}...` : clean;
}

function createRootBranch(): SessionBranch {
  return {
    id: createBranchId(),
    title: '主版本',
    parentBranchId: null,
    parentMessageId: null,
    updatedAt: Date.now(),
    messages: [createMessage('assistant', DEFAULT_SYSTEM_MESSAGE)],
    traceSteps: [],
  };
}

function normalizeBranch(raw: unknown): SessionBranch {
  const candidate = (raw ?? {}) as Partial<SessionBranch>;
  const messages = Array.isArray(candidate.messages)
    ? candidate.messages.map(normalizeMessage)
    : [createMessage('assistant', DEFAULT_SYSTEM_MESSAGE)];

  return {
    id: candidate.id || createBranchId(),
    title: candidate.title || '历史版本',
    parentBranchId: candidate.parentBranchId ?? null,
    parentMessageId: candidate.parentMessageId ?? null,
    updatedAt: typeof candidate.updatedAt === 'number' ? candidate.updatedAt : Date.now(),
    messages,
    traceSteps: Array.isArray(candidate.traceSteps) ? candidate.traceSteps : [],
  };
}

function normalizeSession(raw: unknown): SessionRecord {
  const candidate = (raw ?? {}) as Record<string, unknown>;
  let branches: SessionBranch[] = [];

  if (Array.isArray(candidate.branches) && candidate.branches.length > 0) {
    branches = candidate.branches.map((item) => normalizeBranch(item));
  } else {
    const fallbackMessages = Array.isArray(candidate.messages)
      ? candidate.messages.map(normalizeMessage)
      : [createMessage('assistant', DEFAULT_SYSTEM_MESSAGE)];

    branches = [
      {
        id: createBranchId(),
        title: '主版本',
        parentBranchId: null,
        parentMessageId: null,
        updatedAt: typeof candidate.updatedAt === 'number' ? candidate.updatedAt : Date.now(),
        messages: fallbackMessages,
        traceSteps: Array.isArray(candidate.traceSteps)
          ? (candidate.traceSteps as ReactTraceStep[])
          : [],
      },
    ];
  }

  const activeBranchId =
    typeof candidate.activeBranchId === 'string' ? candidate.activeBranchId : branches[0].id;

  return {
    id: typeof candidate.id === 'string' ? candidate.id : createChatId(),
    title: typeof candidate.title === 'string' ? candidate.title : '新对话',
    updatedAt: typeof candidate.updatedAt === 'number' ? candidate.updatedAt : Date.now(),
    modelProfile: typeof candidate.modelProfile === 'string' ? candidate.modelProfile : 'balanced',
    streaming: Boolean(candidate.streaming ?? true),
    pinned: Boolean(candidate.pinned),
    archived: Boolean(candidate.archived),
    workspaceId:
      typeof candidate.workspaceId === 'string' ? candidate.workspaceId : DEFAULT_WORKSPACE,
    activeBranchId,
    branches,
  };
}

function createSession(): SessionRecord {
  const id = createChatId();
  const rootBranch = createRootBranch();

  return {
    id,
    title: '新对话',
    updatedAt: Date.now(),
    modelProfile: 'balanced',
    streaming: true,
    pinned: false,
    archived: false,
    workspaceId: DEFAULT_WORKSPACE,
    activeBranchId: rootBranch.id,
    branches: [rootBranch],
  };
}

function formatTime(value: number): string {
  return new Date(value).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatSessionTime(value: number): string {
  const date = new Date(value);
  const now = new Date();
  const time = date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  });
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();

  if (sameDay) {
    return `今天 ${time}`;
  }

  return `${date.toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  })} ${time}`;
}

function shortId(id: string): string {
  return id.slice(0, 10);
}

function workspaceLabel(workspace: string): string {
  return workspace === DEFAULT_WORKSPACE ? '默认分组' : workspace;
}

function renderMarkdown(content: string): string {
  if (!content?.trim()) {
    return '<p>等待输出...</p>';
  }
  const html = marked.parse(content) as string;
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ['data-code'],
  });
}

const cached = safeParse(localStorage.getItem(STORAGE_KEY));
const legacy = safeParse(localStorage.getItem(LEGACY_STORAGE_KEY));
const bootstrap = Object.keys(cached).length > 0 ? cached : legacy;
const initialApiKey = DEFAULT_SERVICE_KEY;

const darkMode = ref(Boolean(bootstrap.darkMode));
const activeView = ref<ConsoleView>('chat');
const settingsVisible = ref(false);
const apiKeyInput = ref(initialApiKey);
const tenantInput = ref((bootstrap.tenantId as string | undefined) ?? 'public');
const token = ref((bootstrap.token as string | undefined) ?? '');
const refreshToken = ref((bootstrap.refreshToken as string | undefined) ?? '');
const sessionSearch = ref((bootstrap.sessionSearch as string | undefined) ?? '');
const workspaceFilter = ref((bootstrap.workspaceFilter as string | undefined) ?? 'all');
const showArchivedSessions = ref(Boolean(bootstrap.showArchivedSessions));
const workspaceIds = ref<string[]>(
  Array.isArray(bootstrap.workspaceIds)
    ? (bootstrap.workspaceIds as unknown[])
        .filter((item): item is string => typeof item === 'string')
        .map((item) => item.trim().toLowerCase())
        .filter(Boolean)
    : [],
);
const hasBootstrapSessions = Array.isArray(bootstrap.sessions) && bootstrap.sessions.length > 0;

const sessions = ref<SessionRecord[]>(
  hasBootstrapSessions
    ? (bootstrap.sessions as unknown[]).map((item) => normalizeSession(item))
    : [createSession()],
);

const activeSessionId = ref(
  (bootstrap.activeSessionId as string | undefined) ?? sessions.value[0].id,
);

const activeSession = computed(() => {
  const found = sessions.value.find((item) => item.id === activeSessionId.value);
  return found ?? sessions.value[0];
});

const activeBranch = computed(() => {
  const session = activeSession.value;
  return (
    session.branches.find((branch) => branch.id === session.activeBranchId) ?? session.branches[0]
  );
});

const chatId = ref(activeSession.value.id);
const modelProfile = ref(activeSession.value.modelProfile);
const streaming = ref(activeSession.value.streaming);
const messages = ref<ChatMessage[]>([...activeBranch.value.messages]);
const traceSteps = ref<ReactTraceStep[]>([...activeBranch.value.traceSteps]);
const traceDurationMs = computed(() => {
  if (!traceSteps.value.length) return 0;
  // Estimate ~2s per step as rough timing if not available
  return traceSteps.value.length * 2000;
});

const authLoading = ref(false);
const sending = ref(false);
const isStreamingResponse = ref(false);
const hydrating = ref(true);
const prompt = ref('');
const messageContainer = ref<HTMLElement | null>(null);
const welcomeFileInput = ref<HTMLInputElement | null>(null);
const currentAbortController = ref<AbortController | null>(null);
const workspaceDraft = ref('');
const editingMessageId = ref<string | null>(null);
const editingMessageDraft = ref('');
const streamPhase = ref<StreamPhase>('idle');
const streamStatusDetail = ref('');
const cloudSyncing = ref(false);
const costSummary = ref<TenantCostSummary | null>(null);
const answerFeedbackMap = ref<Record<string, number>>({});
const answerFeedbackLoading = ref<Record<string, boolean>>({});
const evalDatasets = ref<EvalDataset[]>([]);
const evalSelectedDatasetId = ref((bootstrap.evalSelectedDatasetId as string | undefined) ?? '');
const evalComparison = ref<EvalComparison | null>(null);
const evalLoading = ref(false);
const evalCreating = ref(false);
const evalRunning = ref(false);
const evalReportExporting = ref(false);
const uploadingLegalFile = ref(false);
const isDraggingFile = ref(false);
const citationPreviewVisible = ref(false);
const citationPreviewLoading = ref(false);
const citationPreviewError = ref('');
const citationPreviewCard = ref<CitationCard | null>(null);
const citationPreviewPdfUrl = ref('');
const evalDatasetName = ref('LexScope 法律知识库评测集');
const evalDatasetDescription = ref('检索命中、引用覆盖与回答可靠性检查集');
const evalDatasetJson = ref(JSON.stringify(DEFAULT_EVAL_DATASET, null, 2));

const messageHeights = ref<Record<string, number>>({});
const viewportHeight = ref(0);
const scrollTop = ref(0);
const messageRowElements = new Map<string, HTMLElement>();
let resizeObserver: ResizeObserver | null = null;
let streamResetTimer: number | null = null;
let fileDragDepth = 0;
let autoCloudSyncTimer: number | null = null;
let autoCloudSyncInFlight = false;
let lastAutoCloudSyncErrorAt = 0;
const pendingAutoCloudSyncSessionIds = new Set<string>();

const welcomePrompts = [
  '这份合同有哪些风险？',
  '帮我提取争议焦点',
  '生成法律分析报告',
];

const followUpRules = [
  {
    keywords: ['门牌号', '地址', '房屋位置', '房屋信息', '坐落', '租赁房屋'],
    questions: [
      '门牌号或房屋地址不明确，会不会影响租赁合同效力？',
      '需要哪些证据证明实际租赁房屋的位置？',
      '如果合同地址和实际房屋不一致，承租人应如何主张权利？',
      '这类信息缺失在诉讼中会增加哪些举证风险？',
    ],
  },
  {
    keywords: ['证据', '举证', '证明', '聊天记录', '转账', '收据', '发票', '录音'],
    questions: [
      '目前还缺哪些关键证据会影响判断结果？',
      '聊天记录、转账记录或收据分别能证明哪些事实？',
      '如果对方否认相关事实，应该怎样组织证据链？',
      '哪些证据需要尽快固定，避免后续无法举证？',
    ],
  },
  {
    keywords: ['诉讼', '起诉', '法院', '仲裁', '管辖', '时效', '判决', '执行'],
    questions: [
      '如果进入诉讼，应该优先准备哪些材料？',
      '这个争议应当向哪个法院或机构主张权利？',
      '本案有没有诉讼时效或举证期限方面的风险？',
      '如果胜诉后对方仍不履行，后续执行会遇到哪些问题？',
    ],
  },
  {
    keywords: ['合同', '条款', '违约', '履行', '解除', '违约金', '押金', '租金', '定金'],
    questions: [
      '合同里哪些条款最容易引发违约责任？',
      '一方想解除合同，需要满足哪些条件？',
      '押金、租金或违约金应当如何计算更合理？',
      '如果对方不按合同履行，可以优先主张哪些权利？',
    ],
  },
  {
    keywords: ['风险', '责任', '赔偿', '损失', '过错', '不利后果'],
    questions: [
      '当前最主要的法律风险具体落在哪一方？',
      '如果风险实际发生，可能产生哪些赔偿或违约责任？',
      '有哪些做法可以先降低后续争议风险？',
      '哪些事实会改变责任划分结果？',
    ],
  },
  {
    keywords: ['出租人', '承租人', '租赁', '房东', '租客', '收回房屋'],
    questions: [
      '出租人能不能因为家庭自住提前收回房屋？',
      '承租人逾期支付租金会不会构成违约？',
      '押金是否还能要求全部退还？',
      '租赁关系解除时，双方应当如何交接和结算？',
    ],
  },
  {
    keywords: ['案例', '类案', '裁判', '判例', '裁判规则'],
    questions: [
      '有没有类似案件支持当前分析结论？',
      '法院在同类争议中通常会重点审查哪些事实？',
      '如果类案观点不一致，应优先参考哪些裁判理由？',
      '这个案件和常见类案相比，最关键的差异在哪里？',
    ],
  },
];

const sessionCount = computed(() => sessions.value.length);

const effectiveApiKey = computed(() => apiKeyInput.value.trim() || DEFAULT_SERVICE_KEY);
const canUseRemoteSync = computed(() => Boolean(effectiveApiKey.value || token.value));

const selectedEvalDataset = computed(() =>
  evalDatasets.value.find((dataset) => dataset.datasetId === evalSelectedDatasetId.value),
);

const evalCurrentRun = computed<EvalRun | null>(() => evalComparison.value?.current ?? null);

const evalBaselineRun = computed<EvalRun | null>(() => evalComparison.value?.baseline ?? null);

const evalMetricCards = computed<EvalMetricCard[]>(() => {
  const current = evalCurrentRun.value?.metrics;
  const baseline = evalBaselineRun.value?.metrics;
  return [
    metricCard('runScore', '总体得分', current, baseline, 'percent'),
    metricCard('retrievalHitRate', '检索命中', current, baseline, 'percent'),
    metricCard('citationCoverageRate', '引用覆盖', current, baseline, 'percent'),
    metricCard('answerFaithfulnessScore', '回答可靠性', current, baseline, 'percent'),
    metricCard('avgLatencyMs', '平均耗时', current, baseline, 'ms', true),
    metricCard('failureRate', '失败率', current, baseline, 'percent', true),
  ];
});

const workspaceOptions = computed(() => {
  const options = new Set<string>([DEFAULT_WORKSPACE]);
  workspaceIds.value.forEach((workspace) => {
    options.add(workspace || DEFAULT_WORKSPACE);
  });
  sessions.value.forEach((session) => {
    options.add(session.workspaceId || DEFAULT_WORKSPACE);
  });
  return [...options].sort((a, b) => a.localeCompare(b, 'zh-CN'));
});

const activeWorkspaceId = computed({
  get: () => activeSession.value.workspaceId,
  set: (value: string) => {
    activeSession.value.workspaceId = value || DEFAULT_WORKSPACE;
    persistState();
    scheduleAutoCloudSync(activeSession.value.id);
  },
});

const orderedSessions = computed(() =>
  [...sessions.value].sort((a, b) => b.updatedAt - a.updatedAt),
);

const filteredSessions = computed(() => {
  const keyword = sessionSearch.value.trim().toLowerCase();
  return orderedSessions.value.filter((session) => {
    if (!showArchivedSessions.value && session.archived) {
      return false;
    }

    if (workspaceFilter.value !== 'all' && session.workspaceId !== workspaceFilter.value) {
      return false;
    }

    if (!keyword) {
      return true;
    }

    return (
      session.title.toLowerCase().includes(keyword) || session.id.toLowerCase().includes(keyword)
    );
  });
});

const branchTreeItems = computed<BranchTreeItem[]>(() => {
  const session = activeSession.value;
  const children = new Map<string | null, SessionBranch[]>();

  session.branches.forEach((branch) => {
    const key = branch.parentBranchId ?? null;
    if (!children.has(key)) {
      children.set(key, []);
    }
    children.get(key)?.push(branch);
  });

  children.forEach((list) => {
    list.sort((a, b) => b.updatedAt - a.updatedAt);
  });

  const result: BranchTreeItem[] = [];

  function dfs(parentId: string | null, depth: number): void {
    const list = children.get(parentId) ?? [];
    list.forEach((branch) => {
      result.push({ branch, depth });
      dfs(branch.id, depth + 1);
    });
  }

  dfs(null, 0);
  return result;
});

const isEmptyConversation = computed(() => {
  return !messages.value.some((item) => {
    const content = item.content.trim();
    return (
      item.role === 'user' || (item.role === 'assistant' && content !== DEFAULT_SYSTEM_MESSAGE)
    );
  });
});

function shouldShowFollowUpSuggestions(message: ChatMessage): boolean {
  const content = message.content.trim();
  return (
    message.role === 'assistant' &&
    message.state !== 'pending' &&
    message.state !== 'streaming' &&
    message.state !== 'error' &&
    message.state !== 'stopped' &&
    Boolean(content) &&
    content !== DEFAULT_SYSTEM_MESSAGE &&
    !content.startsWith('已收到《') &&
    !content.startsWith('当前未完成登录或模型配置') &&
    !content.startsWith('会话已重置') &&
    !content.startsWith('请求失败') &&
    content !== '输出已手动停止。'
  );
}

function previousUserQuestion(messageIndex: number): string {
  for (let i = messageIndex - 1; i >= 0; i -= 1) {
    const candidate = messages.value[i];
    if (candidate?.role === 'user' && candidate.content.trim()) {
      return candidate.content.trim();
    }
  }
  return '';
}

function followUpSuggestionsFor(message: ChatMessage, messageIndex: number): string[] {
  const source = `${previousUserQuestion(messageIndex)}\n${message.content}`.toLowerCase();
  const suggestions: string[] = [];

  const addQuestion = (question: string) => {
    if (suggestions.length < 4 && !suggestions.includes(question)) {
      suggestions.push(question);
    }
  };

  followUpRules.forEach((rule) => {
    if (suggestions.length >= 4) {
      return;
    }
    const matched = rule.keywords.some((keyword) => source.includes(keyword.toLowerCase()));
    if (matched) {
      rule.questions.forEach(addQuestion);
    }
  });

  [
    '这份资料里最需要优先核实的关键事实是什么？',
    '如果对方不认可这些事实，我需要准备哪些证据？',
    '这个问题适合先协商解决，还是需要准备诉讼方案？',
    '如果要形成正式法律分析报告，还需要补充哪些材料？',
  ].forEach(addQuestion);

  return suggestions.slice(0, Math.max(2, Math.min(4, suggestions.length)));
}

const messageMetrics = computed<MessageMetric[]>(() => {
  let offset = 0;
  return messages.value.map((item, index) => {
    const height = messageHeights.value[item.id] ?? ESTIMATED_ROW_HEIGHT;
    const metric = {
      item,
      index,
      offset,
      height,
    };
    offset += height;
    return metric;
  });
});

const totalVirtualHeight = computed(() => {
  const metrics = messageMetrics.value;
  if (metrics.length === 0) {
    return 0;
  }
  const last = metrics[metrics.length - 1];
  return last.offset + last.height;
});

function findMetricIndexByOffset(targetOffset: number): number {
  const metrics = messageMetrics.value;
  if (metrics.length === 0) {
    return 0;
  }

  let left = 0;
  let right = metrics.length - 1;
  let answer = metrics.length - 1;

  while (left <= right) {
    const mid = (left + right) >> 1;
    const metric = metrics[mid];
    if (metric.offset + metric.height >= targetOffset) {
      answer = mid;
      right = mid - 1;
    } else {
      left = mid + 1;
    }
  }

  return answer;
}

const virtualRange = computed(() => {
  const total = messages.value.length;
  if (total === 0) {
    return { start: 0, end: -1 };
  }

  const startAnchor = Math.max(0, scrollTop.value - viewportHeight.value * 0.8);
  const endAnchor = scrollTop.value + viewportHeight.value * 1.8;

  const start = Math.max(0, findMetricIndexByOffset(startAnchor) - OVERSCAN_COUNT);
  const end = Math.min(total - 1, findMetricIndexByOffset(endAnchor) + OVERSCAN_COUNT);

  return { start, end };
});

const virtualMessages = computed(() => {
  const metrics = messageMetrics.value;
  const { start, end } = virtualRange.value;
  if (end < start) {
    return [];
  }
  return metrics.slice(start, end + 1);
});

const virtualTopSpacer = computed(() => virtualMessages.value[0]?.offset ?? 0);

const virtualBottomSpacer = computed(() => {
  if (virtualMessages.value.length === 0) {
    return 0;
  }

  const last = virtualMessages.value[virtualMessages.value.length - 1];
  return Math.max(0, totalVirtualHeight.value - (last.offset + last.height));
});

const streamStatusLabel = computed(() => {
  switch (streamPhase.value) {
    case 'thinking':
      return '思考中';
    case 'tool':
      return '查找资料中';
    case 'streaming':
      return '生成中';
    case 'done':
      return '已完成';
    case 'error':
      return '失败';
    case 'stopped':
      return '已停止';
    default:
      return '空闲';
  }
});

const streamStatusTagType = computed(() => {
  switch (streamPhase.value) {
    case 'thinking':
      return 'warning';
    case 'tool':
      return 'success';
    case 'streaming':
      return 'primary';
    case 'done':
      return 'success';
    case 'error':
      return 'danger';
    case 'stopped':
      return 'info';
    default:
      return 'info';
  }
});

function scheduleStreamReset(): void {
  if (streamResetTimer) {
    window.clearTimeout(streamResetTimer);
  }
  streamResetTimer = window.setTimeout(() => {
    streamPhase.value = 'idle';
    streamStatusDetail.value = '';
    streamResetTimer = null;
  }, 1800);
}

function persistState(): void {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      darkMode: darkMode.value,
      activeView: activeView.value,
      apiKey: apiKeyInput.value,
      tenantId: tenantInput.value,
      token: token.value,
      refreshToken: refreshToken.value,
      evalSelectedDatasetId: evalSelectedDatasetId.value,
      activeSessionId: activeSessionId.value,
      sessionSearch: sessionSearch.value,
      workspaceFilter: workspaceFilter.value,
      workspaceIds: workspaceIds.value,
      showArchivedSessions: showArchivedSessions.value,
      sessions: sessions.value,
    }),
  );
}

function authContext() {
  const apiKey = effectiveApiKey.value;
  return {
    token: apiKey ? undefined : token.value || undefined,
    apiKey,
    tenantId: tenantInput.value.trim() || 'public',
  };
}

function ensureEvaluationAuth(): boolean {
  if (canUseRemoteSync.value) {
    return true;
  }
  ElMessage.warning('当前未完成登录或服务配置，请前往设置检查。');
  return false;
}

function activateView(view: ConsoleView): void {
  activeView.value = view;
  persistState();
  if (
    view === 'evaluation' &&
    canUseRemoteSync.value &&
    evalDatasets.value.length === 0 &&
    !evalLoading.value
  ) {
    void loadEvalDatasets();
  }
}

function openDeveloperEvaluation(): void {
  settingsVisible.value = false;
  activateView('evaluation');
}

function prepareUploadPrompt(): void {
  prompt.value =
    '请将法律资料粘贴在这里，并在资料后写下你的问题。示例：请基于以下合同条款，分析主要风险并给出依据。';
}

function openWelcomeFilePicker(): void {
  welcomeFileInput.value?.click();
}

async function handleWelcomeFileSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || uploadingLegalFile.value) {
    return;
  }
  await processLegalFile(file);
}

async function processLegalFile(file: File): Promise<void> {
  const fileName = file.name || '法律资料';
  const lowerName = fileName.toLowerCase();
  if (lowerName.endsWith('.txt')) {
    const text = (await file.text()).trim();
    prompt.value = text
      ? `请基于以下法律资料，分析主要问题并给出依据：\n\n${text.slice(0, 12000)}`
      : '请基于我上传的法律资料，分析主要问题并给出依据。';
    ElMessage.success('已读取 TXT 内容，可以直接提问');
    return;
  }

  if (lowerName.endsWith('.doc') || lowerName.endsWith('.docx')) {
    ElMessage.warning('当前请先将 Word 另存为 PDF，或复制正文后直接提问。');
    return;
  }

  if (!lowerName.endsWith('.pdf')) {
    ElMessage.warning('请上传 PDF、Word 或 TXT 格式的法律资料。');
    return;
  }

  uploadingLegalFile.value = true;
  try {
    try {
      await uploadLegalFile(chatId.value, file, authContext());
    } catch (error) {
      if (!isAuthConfigurationError(error) || effectiveApiKey.value === DEFAULT_SERVICE_KEY) {
        throw error;
      }
      apiKeyInput.value = DEFAULT_SERVICE_KEY;
      token.value = '';
      refreshToken.value = '';
      persistState();
      await uploadLegalFile(chatId.value, file, authContext());
    }
    const hint = `已收到《${fileName}》。你可以直接输入想分析的问题。`;
    if (messages.value.length === 1 && messages.value[0].content === DEFAULT_SYSTEM_MESSAGE) {
      messages.value[0].content = hint;
    } else {
      messages.value.push(createMessage('assistant', hint));
    }
    syncCurrentSessionBranch();
    persistState();
    scheduleAutoCloudSync();
    await scrollToBottom(true);
    ElMessage.success('资料已上传，可以开始提问');
  } catch (error) {
    showUploadError(error);
  } finally {
    uploadingLegalFile.value = false;
  }
}

function isFileDrag(event: DragEvent): boolean {
  return Array.from(event.dataTransfer?.types ?? []).includes('Files');
}

function handlePageDragEnter(event: DragEvent): void {
  if (!isFileDrag(event)) {
    return;
  }
  fileDragDepth += 1;
  isDraggingFile.value = true;
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy';
  }
}

function handlePageDragOver(event: DragEvent): void {
  if (!isFileDrag(event)) {
    return;
  }
  isDraggingFile.value = true;
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy';
  }
}

function handlePageDragLeave(event: DragEvent): void {
  if (!isFileDrag(event)) {
    return;
  }
  fileDragDepth = Math.max(0, fileDragDepth - 1);
  if (fileDragDepth === 0) {
    isDraggingFile.value = false;
  }
}

async function handlePageFileDrop(event: DragEvent): Promise<void> {
  if (!isFileDrag(event)) {
    return;
  }
  fileDragDepth = 0;
  isDraggingFile.value = false;
  const file = event.dataTransfer?.files?.[0];
  if (!file) {
    return;
  }
  if (uploadingLegalFile.value) {
    ElMessage.warning('资料正在上传，请稍后再试。');
    return;
  }
  await processLegalFile(file);
}

function prepareReportPrompt(): void {
  prompt.value = '请基于当前资料和问答记录，生成一份结构化法律分析报告。';
}

function scrollToRecords(): void {
  document.querySelector('.session-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function metricCard(
  key: keyof EvalMetricSummary,
  label: string,
  current: EvalMetricSummary | undefined,
  baseline: EvalMetricSummary | undefined,
  unit: 'percent' | 'ms',
  lowerIsBetter = false,
): EvalMetricCard {
  const currentValue = current?.[key];
  const baselineValue = baseline?.[key];
  const hasCurrent = typeof currentValue === 'number';
  const hasBaseline = typeof baselineValue === 'number';
  let delta = '参考 -';
  let deltaClass = 'neutral';
  if (hasCurrent && hasBaseline) {
    const diff = currentValue - baselineValue;
    const good = lowerIsBetter ? diff <= 0 : diff >= 0;
    delta = `${diff >= 0 ? '+' : ''}${formatMetricValue(diff, unit)}`;
    deltaClass = good ? 'good' : 'bad';
  }
  return {
    key,
    label,
    current: hasCurrent ? formatMetricValue(currentValue, unit) : '-',
    delta,
    deltaClass,
  };
}

function formatMetricValue(value: number, unit: 'percent' | 'ms'): string {
  if (unit === 'ms') {
    return `${value.toFixed(0)}ms`;
  }
  return formatPercent(value);
}

function formatPercent(value: number | undefined): string {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '-';
  }
  return `${(value * 100).toFixed(1)}%`;
}

function formatRunScore(value: number | undefined): string {
  return typeof value === 'number' ? formatPercent(value) : '-';
}

const FRIENDLY_CONFIGURATION_ERROR = '当前未完成登录或服务配置，请前往设置检查。';

function errorDetail(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message.trim();
  }
  if (typeof error === 'string' && error.trim()) {
    return error.trim();
  }
  return fallback;
}

function friendlyErrorMessage(error: unknown, fallback: string): string {
  const detail = errorDetail(error, fallback).toLowerCase();
  if (detail.includes('http 401') || detail.includes('unauthorized')) {
    return '权限校验失败，请检查服务配置或刷新页面后重试。';
  }
  if (detail.includes('http 403') || detail.includes('forbidden')) {
    return '请求被权限或跨域规则拦截，请检查服务配置后重试。';
  }
  if (detail.includes('cors') || detail.includes('origin')) {
    return '浏览器请求被跨域规则拦截，请通过项目提供的 8088 入口访问。';
  }
  if (detail.includes('budget exceeded') || detail.includes('预算') || detail.includes('budget')) {
    return '本次请求被用量预算保护拦截，请稍后重试或调整后端预算配置。';
  }
  if (detail.includes('timeout') || detail.includes('timed out')) {
    return '模型响应超时，请稍后重试。';
  }
  if (detail.includes('stream') || detail.includes('sse')) {
    return '实时输出连接失败，请稍后重试，或切换为单次输出。';
  }
  return fallback;
}

function showFriendlyError(error: unknown, fallback: string): void {
  console.warn(fallback, error);
  ElMessage.error(friendlyErrorMessage(error, fallback));
}

function uploadErrorMessage(error: unknown): string {
  const detail = errorDetail(error, '').toLowerCase();
  if (detail.includes('http 413') || detail.includes('request entity too large')) {
    return '文件过大，请上传 100MB 以内的 PDF。';
  }
  if (detail.includes('only pdf') || detail.includes('invalid pdf')) {
    return '当前仅支持可正常解析的 PDF 文件，请确认文件格式正确。';
  }
  if (detail.includes('http 401') || detail.includes('http 403')) {
    return '上传权限校验失败，已使用默认上传配置，请刷新页面后再试。';
  }
  return '资料上传失败，请换一个 PDF 文件再试。';
}

function showUploadError(error: unknown): void {
  console.warn('资料上传失败', error);
  ElMessage.error(uploadErrorMessage(error));
}

function isAuthConfigurationError(error: unknown): boolean {
  const detail = errorDetail(error, '').toLowerCase();
  return (
    detail.includes('http 401') ||
    detail.includes('http 403') ||
    detail.includes('missing or invalid credentials') ||
    detail.includes('unauthorized') ||
    detail.includes('forbidden')
  );
}

function buildFriendlyErrorMessage(error: unknown, fallback: string): string {
  const detail = errorDetail(error, fallback).replace(/```/g, "'''");
  return `${friendlyErrorMessage(error, fallback)}\n\n<details><summary>展开详情</summary>\n\n\`\`\`text\n${detail}\n\`\`\`\n\n</details>`;
}

function normalizeEvalCase(raw: Record<string, unknown>, index: number): EvalCaseCreate {
  const expectedKeywords = raw.expectedKeywords ?? raw.expected_keywords;
  const expectedCitations = raw.expectedCitations ?? raw.expected_citations;
  const forbiddenKeywords = raw.forbiddenKeywords ?? raw.forbidden_keywords;
  return {
    caseId: String(raw.caseId ?? raw.id ?? `case-${index + 1}`).trim(),
    category: String(raw.category ?? 'rag').trim(),
    chatId: String(raw.chatId ?? raw.chat_id ?? '').trim(),
    question: String(raw.question ?? '').trim(),
    expectedKeywords: Array.isArray(expectedKeywords) ? expectedKeywords.map(String) : [],
    expectedCitations: Array.isArray(expectedCitations) ? expectedCitations.map(String) : [],
    forbiddenKeywords: Array.isArray(forbiddenKeywords) ? forbiddenKeywords.map(String) : [],
  };
}

function parseEvalDatasetJson(): EvalCaseCreate[] {
  const parsed = JSON.parse(evalDatasetJson.value) as unknown;
  let rawCases: unknown[] = [];
  if (Array.isArray(parsed)) {
    rawCases = parsed;
  } else if (parsed && typeof parsed === 'object') {
    const objectValue = parsed as Record<string, unknown>;
    if (Array.isArray(objectValue.cases)) {
      rawCases = objectValue.cases;
    } else if (objectValue.paths && typeof objectValue.paths === 'object') {
      Object.values(objectValue.paths as Record<string, unknown>).forEach((pathValue) => {
        if (pathValue && typeof pathValue === 'object') {
          const cases = (pathValue as Record<string, unknown>).cases;
          if (Array.isArray(cases)) {
            rawCases.push(...cases);
          }
        }
      });
    }
  }

  const cases = rawCases
    .filter((item): item is Record<string, unknown> => Boolean(item && typeof item === 'object'))
    .map(normalizeEvalCase)
    .filter((item) => item.question);
  if (cases.length === 0) {
    throw new Error('检查集没有可用问题');
  }
  return cases;
}

async function loadEvalDatasets(): Promise<void> {
  if (!ensureEvaluationAuth()) {
    return;
  }
  evalLoading.value = true;
  try {
    evalDatasets.value = await listEvalDatasets(authContext());
    if (!evalSelectedDatasetId.value && evalDatasets.value.length > 0) {
      evalSelectedDatasetId.value = evalDatasets.value[0].datasetId;
    }
    if (evalSelectedDatasetId.value) {
      await loadEvalComparison(evalSelectedDatasetId.value);
    }
    persistState();
  } catch (error) {
    showFriendlyError(error, '检查集加载失败');
  } finally {
    evalLoading.value = false;
  }
}

async function loadEvalComparison(datasetId: string): Promise<void> {
  if (!ensureEvaluationAuth()) {
    return;
  }
  try {
    evalComparison.value = await getEvalComparison(datasetId, authContext());
  } catch (error) {
    evalComparison.value = null;
    showFriendlyError(error, '检查结果加载失败');
  }
}

async function selectEvalDataset(datasetId: string): Promise<void> {
  evalSelectedDatasetId.value = datasetId;
  persistState();
  await loadEvalComparison(datasetId);
}

async function createEvalDatasetFromJson(): Promise<void> {
  if (!ensureEvaluationAuth()) {
    return;
  }
  evalCreating.value = true;
  try {
    const created = await createEvalDataset(
      {
        name: evalDatasetName.value.trim(),
        description: evalDatasetDescription.value.trim(),
        cases: parseEvalDatasetJson(),
      },
      authContext(),
    );
    evalDatasets.value = [created, ...evalDatasets.value];
    evalSelectedDatasetId.value = created.datasetId;
    await loadEvalComparison(created.datasetId);
    persistState();
    ElMessage.success('检查集已创建');
  } catch (error) {
    showFriendlyError(error, '检查集创建失败');
  } finally {
    evalCreating.value = false;
  }
}

async function runSelectedEvalDataset(): Promise<void> {
  if (!ensureEvaluationAuth()) {
    return;
  }
  if (!evalSelectedDatasetId.value) {
    return;
  }
  evalRunning.value = true;
  try {
    const run = await triggerEvalRun(
      evalSelectedDatasetId.value,
      {
        modelProfile: modelProfile.value,
        chatIdPrefix: 'eval-studio',
      },
      authContext(),
    );
    evalComparison.value = {
      dataset: selectedEvalDataset.value ??
        evalComparison.value?.dataset ?? {
          datasetId: run.datasetId,
          tenantId: run.tenantId,
          name: run.datasetId,
          caseCount: run.metrics.totalCases,
          createdAt: run.createdAt,
          updatedAt: run.createdAt,
        },
      baseline: evalComparison.value?.baseline ?? null,
      current: run,
    };
    ElMessage.success('检查完成');
  } catch (error) {
    showFriendlyError(error, '检查运行失败');
  } finally {
    evalRunning.value = false;
  }
}

async function markCurrentEvalRunBaseline(): Promise<void> {
  if (!ensureEvaluationAuth()) {
    return;
  }
  const run = evalCurrentRun.value;
  if (!run) {
    return;
  }
  try {
    await markEvalRunBaseline(run.runId, authContext());
    await loadEvalComparison(run.datasetId);
    ElMessage.success('参考结果已更新');
  } catch (error) {
    showFriendlyError(error, '参考结果更新失败');
  }
}

async function downloadEvalReport(): Promise<void> {
  if (!ensureEvaluationAuth()) {
    return;
  }
  const run = evalCurrentRun.value;
  if (!run) {
    return;
  }
  evalReportExporting.value = true;
  try {
    const report = await exportEvalRunReport(run.runId, authContext());
    const blob = new Blob([report], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `rag-evaluation-${run.runId}.md`;
    link.click();
    URL.revokeObjectURL(url);
    ElMessage.success('报告已导出');
  } catch (error) {
    showFriendlyError(error, '报告导出失败');
  } finally {
    evalReportExporting.value = false;
  }
}

async function refreshCostSummary(): Promise<void> {
  if (!canUseRemoteSync.value) {
    costSummary.value = null;
    return;
  }
  try {
    costSummary.value = await getTenantCostSummary(authContext());
  } catch {
    // Keep UI usable even when cost endpoint is unavailable.
  }
}

function normalizeRemoteSession(raw: unknown): SessionRecord {
  return normalizeSession(raw);
}

function normalizeWorkspaceId(value: string): string {
  return value.trim().toLowerCase() || DEFAULT_WORKSPACE;
}

function rememberWorkspaceIds(values: string[]): void {
  const next = new Set(workspaceIds.value.map(normalizeWorkspaceId));
  values.forEach((value) => {
    const normalized = normalizeWorkspaceId(value);
    if (normalized !== DEFAULT_WORKSPACE) {
      next.add(normalized);
    }
  });
  workspaceIds.value = [...next].sort((a, b) => a.localeCompare(b, 'zh-CN'));
}

function armAutoCloudSyncTimer(): void {
  if (autoCloudSyncTimer) {
    window.clearTimeout(autoCloudSyncTimer);
  }
  autoCloudSyncTimer = window.setTimeout(() => {
    autoCloudSyncTimer = null;
    void flushAutoCloudSyncQueue();
  }, AUTO_CLOUD_SYNC_DELAY_MS);
}

function scheduleAutoCloudSync(sessionId = activeSessionId.value): void {
  if (hydrating.value || !canUseRemoteSync.value || !sessionId) {
    return;
  }

  pendingAutoCloudSyncSessionIds.add(sessionId);
  armAutoCloudSyncTimer();
}

function clearPendingAutoCloudSync(sessionId: string): void {
  pendingAutoCloudSyncSessionIds.delete(sessionId);
  if (pendingAutoCloudSyncSessionIds.size === 0 && autoCloudSyncTimer) {
    window.clearTimeout(autoCloudSyncTimer);
    autoCloudSyncTimer = null;
  }
}

function notifyAutoCloudSyncFailure(): void {
  const now = Date.now();
  if (now - lastAutoCloudSyncErrorAt < AUTO_CLOUD_SYNC_ERROR_INTERVAL_MS) {
    return;
  }
  lastAutoCloudSyncErrorAt = now;
  ElMessage.warning('自动保存到云端失败，本地记录已保留');
}

async function flushAutoCloudSyncQueue(): Promise<void> {
  if (pendingAutoCloudSyncSessionIds.size === 0) {
    return;
  }
  if (!canUseRemoteSync.value) {
    pendingAutoCloudSyncSessionIds.clear();
    return;
  }
  if (autoCloudSyncInFlight || sending.value || isStreamingResponse.value) {
    armAutoCloudSyncTimer();
    return;
  }

  const sessionIds = [...pendingAutoCloudSyncSessionIds];
  pendingAutoCloudSyncSessionIds.clear();
  autoCloudSyncInFlight = true;

  let failed = false;
  try {
    if (sessionIds.includes(activeSessionId.value)) {
      syncCurrentSessionBranch();
      persistState();
    }

    for (const sessionId of sessionIds) {
      const session = getSession(sessionId);
      if (!session) {
        continue;
      }

      const snapshotUpdatedAt = session.updatedAt;
      try {
        const saved = await saveSessionState(session as unknown as SessionState, authContext());
        const current = getSession(sessionId);
        if (!current) {
          continue;
        }
        if (current.updatedAt > snapshotUpdatedAt) {
          pendingAutoCloudSyncSessionIds.add(sessionId);
          continue;
        }

        const normalized = normalizeRemoteSession(saved);
        rememberWorkspaceIds([normalized.workspaceId]);
        const index = sessions.value.findIndex((item) => item.id === normalized.id);
        if (index >= 0) {
          sessions.value[index] = normalized;
        } else {
          sessions.value.unshift(normalized);
        }
      } catch {
        failed = true;
      }
    }

    persistState();
  } finally {
    autoCloudSyncInFlight = false;
  }

  if (failed) {
    notifyAutoCloudSyncFailure();
  }
  if (pendingAutoCloudSyncSessionIds.size > 0) {
    armAutoCloudSyncTimer();
  }
}

async function saveSessionSnapshotToCloud(
  session: SessionRecord,
  failureContext: string,
): Promise<void> {
  if (!canUseRemoteSync.value) {
    return;
  }

  clearPendingAutoCloudSync(session.id);
  try {
    const saved = await saveSessionState(session as unknown as SessionState, authContext());
    const normalized = normalizeRemoteSession(saved);
    rememberWorkspaceIds([normalized.workspaceId]);
    const index = sessions.value.findIndex((item) => item.id === normalized.id);
    if (index >= 0) {
      sessions.value[index] = normalized;
    } else {
      sessions.value.unshift(normalized);
    }
    if (activeSessionId.value === normalized.id) {
      loadSession(normalized.id);
    }
    persistState();
    await refreshCostSummary();
  } catch (error) {
    showFriendlyError(error, failureContext);
  }
}

async function loadSessionsFromCloud(options?: { silent?: boolean } | Event): Promise<void> {
  const silent = Boolean(options && 'silent' in options && options.silent);
  if (!canUseRemoteSync.value) {
    if (!silent) {
      ElMessage.warning(FRIENDLY_CONFIGURATION_ERROR);
    }
    return;
  }
  if (!silent) {
    cloudSyncing.value = true;
  }
  try {
    const page = await listSessionStates(authContext(), {
      page: 1,
      pageSize: 200,
      includeArchived: true,
    });
    if (Array.isArray(page.items) && page.items.length > 0) {
      sessions.value = page.items.map((item) => normalizeRemoteSession(item));
      rememberWorkspaceIds(sessions.value.map((session) => session.workspaceId));
      const current =
        sessions.value.find((item) => item.id === activeSessionId.value) ?? sessions.value[0];
      loadSession(current.id);
      persistState();
    }
    await refreshCostSummary();
    if (!silent) {
      ElMessage.success('已同步记录');
    }
  } catch (error) {
    if (!silent) {
      showFriendlyError(error, '记录同步失败');
    }
  } finally {
    if (!silent) {
      cloudSyncing.value = false;
    }
  }
}

async function syncActiveSessionToCloud(): Promise<void> {
  if (!canUseRemoteSync.value) {
    ElMessage.warning(FRIENDLY_CONFIGURATION_ERROR);
    return;
  }
  syncCurrentSessionBranch();
  clearPendingAutoCloudSync(activeSession.value.id);
  cloudSyncing.value = true;
  try {
    const saved = await saveSessionState(
      activeSession.value as unknown as SessionState,
      authContext(),
    );
    const normalized = normalizeRemoteSession(saved);
    const index = sessions.value.findIndex((item) => item.id === normalized.id);
    if (index >= 0) {
      sessions.value[index] = normalized;
    } else {
      sessions.value.unshift(normalized);
    }
    loadSession(normalized.id);
    persistState();
    await refreshCostSummary();
    ElMessage.success('当前记录已保存');
  } catch (error) {
    showFriendlyError(error, '记录保存失败');
  } finally {
    cloudSyncing.value = false;
  }
}

function getSession(sessionId: string): SessionRecord | undefined {
  return sessions.value.find((item) => item.id === sessionId);
}

function getBranch(session: SessionRecord, branchId: string): SessionBranch | undefined {
  return session.branches.find((branch) => branch.id === branchId);
}

function syncCurrentSessionBranch(): void {
  const session = getSession(activeSessionId.value);
  if (!session) {
    return;
  }

  const branch = getBranch(session, session.activeBranchId);
  if (!branch) {
    return;
  }

  session.modelProfile = modelProfile.value;
  session.streaming = streaming.value;

  branch.messages = [...messages.value];
  branch.traceSteps = [...traceSteps.value];
  branch.updatedAt = Date.now();

  const firstUser = branch.messages.find((item) => item.role === 'user');
  if (firstUser?.content?.trim()) {
    branch.title = deriveTitle(firstUser.content);
    session.title = deriveTitle(firstUser.content);
  }

  session.updatedAt = Date.now();
}

function loadSession(sessionId: string): void {
  const session = getSession(sessionId);
  if (!session) {
    return;
  }

  activeSessionId.value = session.id;
  chatId.value = session.id;
  modelProfile.value = session.modelProfile;
  streaming.value = session.streaming;

  const branch = getBranch(session, session.activeBranchId) ?? session.branches[0];
  if (!branch) {
    const rootBranch = createRootBranch();
    session.branches = [rootBranch];
    session.activeBranchId = rootBranch.id;
    messages.value = [...rootBranch.messages];
    traceSteps.value = [...rootBranch.traceSteps];
  } else {
    messages.value = [...branch.messages];
    traceSteps.value = [...branch.traceSteps];
  }

  messageHeights.value = {};
  prompt.value = '';
  editingMessageId.value = null;
  editingMessageDraft.value = '';
  void scrollToBottom(true);
}

function switchSession(sessionId: string): void {
  if (sessionId === activeSessionId.value) {
    return;
  }

  const previousSessionId = activeSessionId.value;
  syncCurrentSessionBranch();
  loadSession(sessionId);
  persistState();
  scheduleAutoCloudSync(previousSessionId);
}

function createAndSwitchSession(): void {
  const previousSessionId = activeSessionId.value;
  syncCurrentSessionBranch();
  const session = createSession();
  sessions.value.unshift(session);
  loadSession(session.id);
  persistState();
  scheduleAutoCloudSync(previousSessionId);
}

function removeSession(sessionId: string): void {
  if (sessions.value.length <= 1) {
    ElMessage.warning('至少保留一条记录');
    return;
  }

  syncCurrentSessionBranch();
  const filtered = sessions.value.filter((item) => item.id !== sessionId);
  sessions.value = filtered;

  if (activeSessionId.value === sessionId) {
    const next = filtered.find((item) => !item.archived) ?? filtered[0];
    loadSession(next.id);
  }

  persistState();
}

async function toggleSessionPin(sessionId: string): Promise<void> {
  const session = getSession(sessionId);
  if (!session) {
    return;
  }
  if (sessionId === activeSessionId.value) {
    syncCurrentSessionBranch();
  }
  session.pinned = !session.pinned;
  session.updatedAt = Date.now();
  persistState();
  await saveSessionSnapshotToCloud(session, '置顶同步失败');
}

async function toggleSessionArchive(sessionId: string): Promise<void> {
  const session = getSession(sessionId);
  if (!session) {
    return;
  }
  if (sessionId === activeSessionId.value) {
    syncCurrentSessionBranch();
  }

  session.archived = !session.archived;
  session.updatedAt = Date.now();

  if (session.archived && !showArchivedSessions.value && activeSessionId.value === sessionId) {
    const next =
      sessions.value.find((item) => item.id !== sessionId && !item.archived) ??
      sessions.value.find((item) => item.id !== sessionId) ??
      createSession();

    if (!sessions.value.find((item) => item.id === next.id)) {
      sessions.value.unshift(next);
    }

    loadSession(next.id);
  }

  persistState();
  await saveSessionSnapshotToCloud(session, '归档同步失败');
}

function createWorkspace(): void {
  const rawValue = workspaceDraft.value.trim();
  if (!rawValue) {
    return;
  }

  const value = normalizeWorkspaceId(rawValue);
  rememberWorkspaceIds([value]);
  activeWorkspaceId.value = value;
  workspaceFilter.value = value;
  workspaceDraft.value = '';
  persistState();
  scheduleAutoCloudSync();
}

function switchBranch(branchId: string): void {
  const session = activeSession.value;
  if (session.activeBranchId === branchId) {
    return;
  }

  syncCurrentSessionBranch();
  session.activeBranchId = branchId;
  loadSession(session.id);
  persistState();
  scheduleAutoCloudSync(session.id);
}

function forkBranch(
  title: string,
  baseMessages: ChatMessage[],
  parentBranchId: string | null,
  parentMessageId: string | null,
): SessionBranch {
  return {
    id: createBranchId(),
    title,
    parentBranchId,
    parentMessageId,
    updatedAt: Date.now(),
    messages: [...baseMessages],
    traceSteps: [],
  };
}

function forkFromCurrent(): void {
  const session = activeSession.value;
  const current = activeBranch.value;

  const branch = forkBranch(`${current.title} · 新版本`, [...messages.value], current.id, null);

  session.branches.unshift(branch);
  session.activeBranchId = branch.id;
  loadSession(session.id);
  persistState();
  scheduleAutoCloudSync(session.id);
  ElMessage.success('已另存为新版本');
}

async function compareWithParent(): Promise<void> {
  const current = activeBranch.value;
  if (!current?.parentBranchId) {
    ElMessage.warning('当前版本没有上一版可对比');
    return;
  }
  if (!canUseRemoteSync.value) {
    ElMessage.warning(FRIENDLY_CONFIGURATION_ERROR);
    return;
  }
  syncCurrentSessionBranch();
  try {
    await syncActiveSessionToCloud();
    const result = await compareSessionBranches(
      activeSession.value.id,
      {
        sourceBranchId: current.id,
        targetBranchId: current.parentBranchId,
      },
      authContext(),
    );
    ElMessage.success(
      `对比完成：共同内容 ${result.commonMessageCount}，当前版本新增 ${result.sourceOnlyCount}，上一版独有 ${result.targetOnlyCount}`,
    );
  } catch (error) {
    showFriendlyError(error, '版本对比失败');
  }
}

async function mergeIntoParent(): Promise<void> {
  const current = activeBranch.value;
  if (!current?.parentBranchId) {
    ElMessage.warning('当前版本没有上一版可采用');
    return;
  }
  if (!canUseRemoteSync.value) {
    ElMessage.warning(FRIENDLY_CONFIGURATION_ERROR);
    return;
  }
  syncCurrentSessionBranch();
  try {
    await syncActiveSessionToCloud();
    const result = await mergeSessionBranches(
      activeSession.value.id,
      {
        sourceBranchId: current.id,
        targetBranchId: current.parentBranchId,
        title: `${current.title} -> ${current.parentBranchId} merge`,
      },
      authContext(),
    );
    const normalized = normalizeRemoteSession(result.session);
    const index = sessions.value.findIndex((item) => item.id === normalized.id);
    if (index >= 0) {
      sessions.value[index] = normalized;
    } else {
      sessions.value.unshift(normalized);
    }
    loadSession(normalized.id);
    persistState();
    ElMessage.success(`已采用当前版本，保留 ${result.mergedMessageCount} 条消息`);
  } catch (error) {
    showFriendlyError(error, '采用当前版本失败');
  }
}

function startEditMessage(message: ChatMessage): void {
  if (message.role !== 'user') {
    return;
  }

  editingMessageId.value = message.id;
  editingMessageDraft.value = message.content;
}

function cancelEditMessage(): void {
  editingMessageId.value = null;
  editingMessageDraft.value = '';
}

async function submitEditAndResend(messageIndex: number, messageId: string): Promise<void> {
  const question = editingMessageDraft.value.trim();
  if (!question) {
    return;
  }

  if (sending.value) {
    ElMessage.warning('请等待当前回答完成');
    return;
  }

  syncCurrentSessionBranch();

  const session = activeSession.value;
  const current = activeBranch.value;
  const baseMessages = messages.value.slice(0, Math.max(0, messageIndex));
  const branch = forkBranch(
    `${deriveTitle(question)} · 修改版`,
    baseMessages,
    current.id,
    messageId,
  );

  session.branches.unshift(branch);
  session.activeBranchId = branch.id;
  loadSession(session.id);

  cancelEditMessage();
  persistState();

  await ask(question, true);
}

function upsertTrace(step: ReactTraceStep): void {
  const index = traceSteps.value.findIndex((item) => item.step === step.step);
  if (index >= 0) {
    traceSteps.value[index] = step;
  } else {
    traceSteps.value.push(step);
    traceSteps.value.sort((a, b) => a.step - b.step);
  }
}

function updateViewport(): void {
  viewportHeight.value = messageContainer.value?.clientHeight ?? 0;
}

function onMessageScroll(): void {
  const element = messageContainer.value;
  if (!element) {
    return;
  }
  scrollTop.value = element.scrollTop;
}

function syncMessageHeight(messageId: string, height: number): void {
  if (height <= 0) {
    return;
  }

  const current = messageHeights.value[messageId] ?? 0;
  if (Math.abs(current - height) <= 1) {
    return;
  }

  messageHeights.value = {
    ...messageHeights.value,
    [messageId]: height,
  };
}

function setMessageRowRef(messageId: string, element: HTMLElement | null): void {
  const previous = messageRowElements.get(messageId);
  if (previous && previous !== element && resizeObserver) {
    resizeObserver.unobserve(previous);
    messageRowElements.delete(messageId);
  }

  if (!element) {
    return;
  }

  messageRowElements.set(messageId, element);
  syncMessageHeight(messageId, Math.ceil(element.getBoundingClientRect().height));
  if (resizeObserver) {
    resizeObserver.observe(element);
  }
}

async function scrollToBottom(force = false): Promise<void> {
  await nextTick();
  const element = messageContainer.value;
  if (!element) {
    return;
  }

  const remaining = element.scrollHeight - element.scrollTop - element.clientHeight;
  if (force || remaining < 180 || sending.value) {
    element.scrollTop = element.scrollHeight;
    scrollTop.value = element.scrollTop;
  }
}

async function handleMarkdownClick(event: MouseEvent): Promise<void> {
  const target = event.target as HTMLElement | null;
  const button = target?.closest('.copy-code-btn') as HTMLElement | null;
  if (!button) {
    return;
  }

  const payload = button.getAttribute('data-code');
  if (!payload) {
    return;
  }

  try {
    const raw = fromBase64(payload);
    await navigator.clipboard.writeText(raw);
    ElMessage.success('代码已复制');
  } catch {
    ElMessage.error('代码复制失败');
  }
}

async function copyMessage(content: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(content);
    ElMessage.success('已复制');
  } catch {
    ElMessage.error('复制失败');
  }
}

function normalizeCitationLike(raw: unknown): CitationLike | null {
  if (typeof raw === 'string') {
    const value = raw.trim();
    return value ? value : null;
  }
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const item = raw as Record<string, unknown>;
  const fileName = stringValue(item.fileName) || stringValue(item.title);
  const snippet = cleanCitationSnippet(stringValue(item.snippet) || stringValue(item.excerpt));
  const pageNumber = numberValue(item.pageNumber);
  const index = numberValue(item.index);
  const debug =
    item.debug && typeof item.debug === 'object' ? (item.debug as Record<string, unknown>) : undefined;
  if (!fileName && !snippet) {
    return null;
  }
  return {
    ...(index ? { index } : {}),
    ...(fileName ? { fileName } : {}),
    ...(pageNumber ? { pageNumber } : {}),
    ...(snippet ? { snippet } : {}),
    ...(debug ? { debug } : {}),
  };
}

function isCitationLike(value: CitationLike | null): value is CitationLike {
  return value !== null;
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function numberValue(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number.parseInt(value.replace(/[^0-9]/g, ''), 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }
  return null;
}

function cleanCitationSnippet(value: string): string {
  return value
    .replace(
      /\b(?:tenant_id|chat_id|job_id|source|source_type|file_name|filename|fileName|chunk_index|page_number|pageNumber|page_index|distance)\s*[:=]\s*[^\s,，;；]+/gi,
      ' ',
    )
    .replace(/\b(?:source|chunk|chunk_index)\s*=\s*[^\s,，;；]+/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function parseLegacyCitation(citation: string): CitationCard {
  const matched = citation.match(/source=([^,，]+)[,，]\s*(?:chunk|chunk_index)=(.+)$/i);
  if (matched) {
    return {
      index: 1,
      fileName: matched[1].trim(),
      debug: {
        source: matched[1].trim(),
        chunk: matched[2].trim(),
      },
    };
  }
  return {
    index: 1,
    fileName: cleanCitationSnippet(citation),
  };
}

function citationCards(message: ChatMessage): CitationCard[] {
  const cards = (message.citations ?? [])
    .map((citation, citationIndex) => toCitationCard(citation, citationIndex + 1))
    .filter((card): card is CitationCard => Boolean(card));
  const seen = new Set<string>();
  return cards.filter((card) => {
    const fingerprint = `${card.fileName}|${card.pageNumber ?? ''}|${card.snippet ?? ''}`;
    if (seen.has(fingerprint)) {
      return false;
    }
    seen.add(fingerprint);
    return true;
  });
}

function toCitationCard(citation: CitationLike, fallbackIndex: number): CitationCard | null {
  if (typeof citation === 'string') {
    const card = parseLegacyCitation(citation);
    card.index = fallbackIndex;
    return card.fileName ? card : null;
  }
  const fileName = citation.fileName?.trim() || citation.title?.trim() || '未知文件';
  const snippet = cleanCitationSnippet(citation.snippet || citation.excerpt || '');
  return {
    index: citation.index || fallbackIndex,
    fileName,
    pageNumber: citation.pageNumber ?? null,
    snippet: snippet.length > 80 ? `${snippet.slice(0, 80)}...` : snippet,
    debug: citation.debug,
  };
}

function cleanAssistantContent(content: string): string {
  return content
    .replace(/^\s*(?:tenant_id|chat_id|job_id|source|source_type|file_name|filename|fileName|chunk_index|page_number|pageNumber|page_index|distance)\s*[:=].*$/gim, '')
    .replace(/^\s*\[?\d*\]?\s*source\s*=.*(?:chunk|chunk_index)\s*=.*$/gim, '')
    .replace(/source\s*=\s*[^,，\n]+[,，]\s*(?:chunk|chunk_index)\s*=\s*[^\s，,。；;]+/gi, '')
    .replace(/\n{2,}(?:---\s*)?(引用来源|参考来源)[:：][\s\S]*$/i, '')
    .trim();
}

function revokeCitationPreviewUrl(): void {
  if (citationPreviewPdfUrl.value) {
    URL.revokeObjectURL(citationPreviewPdfUrl.value.split('#')[0]);
    citationPreviewPdfUrl.value = '';
  }
}

function closeCitationPreview(): void {
  revokeCitationPreviewUrl();
  citationPreviewLoading.value = false;
  citationPreviewError.value = '';
}

async function openCitation(citation: CitationCard): Promise<void> {
  citationPreviewCard.value = citation;
  citationPreviewVisible.value = true;
  citationPreviewLoading.value = true;
  citationPreviewError.value = '';
  revokeCitationPreviewUrl();
  try {
    const blob = await fetchPdfFile(chatId.value, authContext());
    const url = URL.createObjectURL(blob);
    citationPreviewPdfUrl.value = citation.pageNumber ? `${url}#page=${citation.pageNumber}` : url;
  } catch (error) {
    citationPreviewError.value = friendlyErrorMessage(error, '参考来源预览失败');
    showFriendlyError(error, '参考来源预览失败');
  } finally {
    citationPreviewLoading.value = false;
  }
}

function findPreviousUserQuestion(index: number): string {
  for (let i = index - 1; i >= 0; i -= 1) {
    const candidate = messages.value[i];
    if (candidate.role === 'user' && candidate.content.trim()) {
      return candidate.content.trim();
    }
  }
  return '';
}

async function rateAnswer(index: number, message: ChatMessage, rating: number): Promise<void> {
  if (message.role !== 'assistant') {
    return;
  }
  if (!canUseRemoteSync.value) {
    ElMessage.warning(FRIENDLY_CONFIGURATION_ERROR);
    return;
  }
  if (answerFeedbackMap.value[message.id]) {
    ElMessage.info('该回答已评分');
    return;
  }
  answerFeedbackLoading.value = {
    ...answerFeedbackLoading.value,
    [message.id]: true,
  };
  try {
    await submitAnswerFeedback(
      {
        chatId: chatId.value,
        sessionId: activeSession.value.id,
        branchId: activeBranch.value.id,
        messageId: message.id,
        rating,
        question: findPreviousUserQuestion(index),
        answer: message.content,
        comment: rating >= 4 ? '回答有帮助' : '回答需要改进',
      },
      authContext(),
    );
    answerFeedbackMap.value = {
      ...answerFeedbackMap.value,
      [message.id]: rating,
    };
    ElMessage.success('反馈已提交');
  } catch (error) {
    showFriendlyError(error, '反馈提交失败');
  } finally {
    answerFeedbackLoading.value = {
      ...answerFeedbackLoading.value,
      [message.id]: false,
    };
  }
}

function stopGenerating(): void {
  currentAbortController.value?.abort();
}

function clearConversation(): void {
  messages.value = [createMessage('assistant', '会话已重置。你可以继续提问。')];
  traceSteps.value = [];
  answerFeedbackMap.value = {};
  answerFeedbackLoading.value = {};
  prompt.value = '';
  streamPhase.value = 'idle';
  streamStatusDetail.value = '';
  syncCurrentSessionBranch();
  persistState();
  scheduleAutoCloudSync();
}

async function handleLogin(): Promise<void> {
  authLoading.value = true;
  try {
    apiKeyInput.value = effectiveApiKey.value;
    tenantInput.value = tenantInput.value.trim() || 'public';
    token.value = '';
    refreshToken.value = '';
    persistState();
    await refreshCostSummary();
    ElMessage.success('设置已保存');
  } catch (error) {
    showFriendlyError(error, '设置保存失败');
  } finally {
    authLoading.value = false;
  }
}

function clearAuth(): void {
  apiKeyInput.value = DEFAULT_SERVICE_KEY;
  tenantInput.value = 'public';
  token.value = '';
  refreshToken.value = '';
  costSummary.value = null;
  ElMessage.success('已恢复默认服务配置');
  persistState();
}

function sanitizeMessageStates(): void {
  messages.value = messages.value.map((message) => ({
    ...message,
    state:
      message.state === 'pending' || message.state === 'streaming'
        ? 'done'
        : message.state || 'done',
  }));
}

async function ask(question: string, appendUser: boolean): Promise<void> {
  if (!question.trim() || sending.value) {
    return;
  }

  sanitizeMessageStates();

  const assistantMsg: ChatMessage = {
    ...createMessage('assistant', ''),
    citations: [],
    evidence: [],
    state: 'pending',
  };

  if (appendUser) {
    messages.value.push(createMessage('user', question));
  }
  messages.value.push(assistantMsg);
  const assistantIndex = messages.value.length - 1;

  traceSteps.value = [];
  sending.value = true;
  isStreamingResponse.value = streaming.value;
  prompt.value = '';
  streamPhase.value = 'thinking';
  streamStatusDetail.value = '正在准备回答';

  syncCurrentSessionBranch();
  persistState();
  await scrollToBottom(true);

  const controller = new AbortController();
  currentAbortController.value = controller;

  try {
    if (streaming.value) {
      let streamError = '';
      await streamReactChat(
        {
          prompt: question,
          chatId: chatId.value,
          modelProfile: modelProfile.value,
        },
        authContext(),
        (event, payload) => {
          if (event === 'trace') {
            const step = payload as ReactTraceStep;
            upsertTrace(step);
            streamPhase.value = 'tool';
            streamStatusDetail.value = '正在查找相关资料';
            return;
          }

          if (event === 'token') {
            const tokenEvent = payload as ReactTokenEvent;
            messages.value[assistantIndex].state = 'streaming';
            messages.value[assistantIndex].content += tokenEvent.token ?? '';
            streamPhase.value = 'streaming';
            streamStatusDetail.value = '正在生成回答';
            void scrollToBottom();
            return;
          }

          if (event === 'done') {
            const done = payload as ReactChatResponse;
            if (done.trace?.length) {
              traceSteps.value = done.trace;
            }
            if (done.answer?.trim()) {
              messages.value[assistantIndex].content = cleanAssistantContent(done.answer);
            }
            messages.value[assistantIndex].citations = Array.isArray(done.citations)
              ? done.citations.map((item) => normalizeCitationLike(item)).filter(isCitationLike)
              : [];
            messages.value[assistantIndex].evidence = Array.isArray(done.evidence)
              ? done.evidence.map((item) => String(item).trim()).filter(Boolean)
              : [];
            messages.value[assistantIndex].state = 'done';
            streamPhase.value = 'done';
            streamStatusDetail.value = '回答已完成';
            return;
          }

          if (event === 'error') {
            const err = payload as ReactErrorEvent;
            streamError = err.message || 'stream error';
          }
        },
        controller.signal,
      );

      if (streamError) {
        throw new Error(streamError);
      }
    } else {
      const result = await reactChat(
        {
          prompt: question,
          chatId: chatId.value,
          modelProfile: modelProfile.value,
        },
        authContext(),
        controller.signal,
      );
      traceSteps.value = result.trace ?? [];
      messages.value[assistantIndex].content = cleanAssistantContent(result.answer || '系统没有返回内容');
      messages.value[assistantIndex].citations = Array.isArray(result.citations)
        ? result.citations.map((item) => normalizeCitationLike(item)).filter(isCitationLike)
        : [];
      messages.value[assistantIndex].evidence = Array.isArray(result.evidence)
        ? result.evidence.map((item) => String(item).trim()).filter(Boolean)
        : [];
      messages.value[assistantIndex].state = 'done';
      streamPhase.value = 'done';
      streamStatusDetail.value = '回答已完成';
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      ElMessage.info('已停止输出');
      if (!messages.value[assistantIndex].content.trim()) {
        messages.value[assistantIndex].content = '输出已手动停止。';
      }
      messages.value[assistantIndex].state = 'stopped';
      streamPhase.value = 'stopped';
      streamStatusDetail.value = '你手动停止了本次输出';
    } else {
      const detail = errorDetail(error, '请求失败');
      messages.value[assistantIndex].content = buildFriendlyErrorMessage(error, '请求失败');
      messages.value[assistantIndex].state = 'error';
      streamPhase.value = 'error';
      streamStatusDetail.value = detail;
      showFriendlyError(error, '请求失败');
    }
  } finally {
    sending.value = false;
    isStreamingResponse.value = false;
    currentAbortController.value = null;

    syncCurrentSessionBranch();
    persistState();
    scheduleAutoCloudSync();
    await scrollToBottom(true);

    if (
      streamPhase.value === 'done' ||
      streamPhase.value === 'error' ||
      streamPhase.value === 'stopped'
    ) {
      scheduleStreamReset();
    }
  }
}

async function send(): Promise<void> {
  const question = prompt.value.trim();
  await ask(question, true);
}

async function regenerateFrom(assistantIndex: number): Promise<void> {
  for (let i = assistantIndex - 1; i >= 0; i -= 1) {
    const candidate = messages.value[i];
    if (candidate.role === 'user' && candidate.content.trim()) {
      syncCurrentSessionBranch();
      const session = activeSession.value;
      const current = activeBranch.value;
      const baseMessages = messages.value.slice(0, i);
      const branch = forkBranch(
        `${deriveTitle(candidate.content)} · 重新生成`,
        baseMessages,
        current.id,
        candidate.id,
      );
      session.branches.unshift(branch);
      session.activeBranchId = branch.id;
      loadSession(session.id);
      persistState();
      await ask(candidate.content, true);
      return;
    }
  }

  ElMessage.warning('没有找到可重新生成的问题');
}

watch(
  darkMode,
  () => {
    document.documentElement.classList.toggle('dark', darkMode.value);
    persistState();
  },
  { immediate: true },
);

watch([modelProfile, streaming], () => {
  syncCurrentSessionBranch();
  persistState();
  scheduleAutoCloudSync();
});

watch([workspaceFilter, showArchivedSessions, sessionSearch], () => {
  persistState();
});

watch(
  [messages, traceSteps],
  () => {
    syncCurrentSessionBranch();
    persistState();
  },
  { deep: true },
);

watch([apiKeyInput, tenantInput, token, refreshToken], () => {
  persistState();
  if (canUseRemoteSync.value) {
    void refreshCostSummary();
    if (
      activeView.value === 'evaluation' &&
      evalDatasets.value.length === 0 &&
      !evalLoading.value
    ) {
      void loadEvalDatasets();
    }
  } else {
    costSummary.value = null;
    evalDatasets.value = [];
    evalComparison.value = null;
  }
});

watch(
  messages,
  () => {
    const idSet = new Set(messages.value.map((message) => message.id));
    const filtered: Record<string, number> = {};
    Object.entries(messageHeights.value).forEach(([id, height]) => {
      if (idSet.has(id)) {
        filtered[id] = height;
      }
    });
    messageHeights.value = filtered;
    void scrollToBottom();
  },
  { deep: false },
);

onMounted(() => {
  loadSession(activeSessionId.value);
  updateViewport();

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver((entries) => {
      entries.forEach((entry) => {
        const element = entry.target as HTMLElement;
        const messageId = element.dataset.msgId;
        if (!messageId) {
          return;
        }
        syncMessageHeight(messageId, Math.ceil(entry.contentRect.height));
      });
    });
  }

  window.addEventListener('resize', updateViewport);
  window.setTimeout(() => {
    hydrating.value = false;
    void scrollToBottom(true);
  }, 220);

  if (canUseRemoteSync.value) {
    void refreshCostSummary();
    if (!hasBootstrapSessions) {
      void loadSessionsFromCloud({ silent: true });
    }
  }

  if (activeView.value === 'evaluation' && canUseRemoteSync.value) {
    void loadEvalDatasets();
  }

  void scrollToBottom(true);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport);

  if (resizeObserver) {
    messageRowElements.forEach((element) => {
      resizeObserver?.unobserve(element);
    });
    resizeObserver.disconnect();
    resizeObserver = null;
  }

  if (streamResetTimer) {
    window.clearTimeout(streamResetTimer);
    streamResetTimer = null;
  }

  if (autoCloudSyncTimer) {
    window.clearTimeout(autoCloudSyncTimer);
    autoCloudSyncTimer = null;
  }

  revokeCitationPreviewUrl();
});
</script>

<style scoped>
.app-shell {
  height: 100vh;
  min-height: 0;
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  overflow: hidden;
  color: var(--ui-text);
  background: var(--ui-bg);
  font-family: var(--ui-serif);
}

.sidebar {
  height: 100vh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  overflow: hidden;
  border-right: 1px solid var(--ui-border);
  background: var(--ui-card);
}

.drop-overlay {
  position: fixed;
  inset: 12px;
  z-index: 1000;
  display: grid;
  place-items: center;
  border: 1px dashed var(--ui-accent);
  border-radius: 14px;
  background: color-mix(in srgb, var(--ui-card) 88%, transparent);
  pointer-events: none;
}

.drop-overlay div {
  display: grid;
  gap: 8px;
  min-width: min(360px, calc(100vw - 48px));
  padding: 22px 24px;
  border: 1px solid var(--ui-border);
  border-radius: 10px;
  background: var(--ui-card);
  text-align: center;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.16);
}

.drop-overlay strong {
  font-size: 18px;
  color: var(--ui-text);
}

.drop-overlay span {
  font-size: 13px;
  color: var(--ui-muted);
}

.brand-block {
  padding: 6px 2px 8px;
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--ui-muted);
}

h1 {
  margin: 6px 0 0;
  font-size: 24px;
  line-height: 1.18;
  font-weight: 650;
  letter-spacing: 0;
  color: var(--ui-text);
}

.brand-sub {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--ui-muted);
}

.new-chat-btn {
  border: 1px solid var(--ui-accent);
  background: var(--ui-accent);
  color: #ffffff;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}

.new-chat-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px #d8d8d2;
}

.sidebar-footer {
  margin-top: auto;
  padding-top: 4px;
  display: grid;
  gap: 10px;
}

.sidebar-usage-card {
  width: 100%;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 12px 13px;
  background: var(--ui-panel);
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 1.45;
}

.settings-link {
  width: 100%;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 9px 12px;
  background: var(--ui-panel);
  color: var(--ui-text);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  text-align: center;
}

.settings-link:hover {
  border-color: var(--ui-accent);
  color: var(--ui-accent);
}

.sidebar-section,
.tools-panel,
.upload-panel {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 10px;
  background: var(--ui-panel);
  display: grid;
  gap: 10px;
}

.upload-panel button,
.tool-shortcuts button {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 8px 10px;
  background: var(--ui-card);
  color: var(--ui-text);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
}

.upload-panel small {
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 1.45;
}

.tool-shortcuts {
  display: grid;
  gap: 8px;
}

.session-search-block {
  min-width: 0;
}

.session-search-block :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 8px;
  background: var(--ui-card);
  box-shadow: 0 0 0 1px var(--ui-border) inset;
  padding-left: 12px;
  padding-right: 12px;
}

.session-search-block :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--ui-soft) inset;
}

.session-search-block :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--ui-accent) inset,
    0 0 0 3px var(--ui-accent-tint);
}

.session-search-block :deep(.el-input__inner) {
  font-size: 14px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.section-label {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0;
  text-transform: none;
  color: var(--ui-muted);
  font-weight: 650;
}

.section-meta {
  font-size: 12px;
  color: var(--ui-muted);
}

.session-panel {
  padding: 0;
  background: transparent;
}

.history-settings {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--ui-card);
}

.branch-panel-head {
  display: grid;
  gap: 8px;
  padding: 10px;
  border-bottom: 1px solid var(--ui-border-soft);
}

.branch-panel-title {
  margin: 0;
  min-width: 0;
  white-space: nowrap;
  color: var(--ui-muted);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.3;
}

.branch-panel-title span {
  margin-left: 6px;
  color: var(--ui-soft);
  font-weight: 500;
}

.session-list,
.branch-list {
  max-height: 214px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-item {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 8px;
  background: var(--ui-card);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: border-color 160ms ease;
}

.session-item:hover,
.session-item.active {
  border-color: var(--ui-accent);
}

.session-content {
  min-width: 0;
}

.session-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.session-title {
  margin: 0;
  font-size: 13px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta-row {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ui-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.session-actions button {
  border: 0;
  background: transparent;
  color: var(--ui-muted);
  border-radius: 6px;
  padding: 2px 4px;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
}

.session-actions button:hover {
  color: var(--ui-accent);
  background: var(--ui-accent-tint);
}

.branch-head-actions button {
  border: 1px solid var(--ui-border);
  background: var(--ui-sand);
  color: var(--ui-text);
  border-radius: 6px;
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
}

.session-actions .danger {
  color: #9f6b50;
}

.session-actions .danger:hover {
  color: #8b4513;
  background: #f7ebe6;
}

.session-empty {
  font-size: 12px;
  color: var(--ui-muted);
  padding: 6px;
}

.branch-head-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.branch-head-actions button {
  min-width: 0;
  white-space: nowrap;
  padding-left: 4px;
  padding-right: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.branch-item {
  position: relative;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--ui-card);
  cursor: pointer;
}

.branch-item.active {
  border-color: var(--ui-accent);
}

.branch-line {
  width: 10px;
  height: 1px;
  background: var(--ui-muted);
}

.branch-content {
  min-width: 0;
}

.branch-content p,
.branch-content small {
  margin: 0;
}

.branch-content p {
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.branch-content small {
  color: var(--ui-muted);
}

.ops-panel {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--ui-panel);
}

.ops-panel summary {
  list-style: none;
  cursor: pointer;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 500;
  background: var(--ui-card);
}

.ops-panel summary::-webkit-details-marker {
  display: none;
}

.ops-body {
  padding: 12px;
}

.settings-drawer {
  display: grid;
  gap: 14px;
}

.settings-card {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--ui-panel);
}

.developer-tools {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.developer-field {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.developer-field span {
  color: var(--ui-muted);
  font-size: 12px;
  font-weight: 650;
}

.developer-control,
.developer-full-button {
  width: 100%;
}

.developer-switch-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.developer-button-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.developer-button-pair .el-button {
  width: 100%;
  min-width: 0;
}

.developer-tools .stream-detail {
  display: block;
  padding-top: 2px;
  line-height: 1.5;
}

.auth-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.full-width {
  width: 100%;
}

:deep(.el-button),
:deep(.el-input__inner),
:deep(.el-textarea__inner),
:deep(.el-select__placeholder),
:deep(.el-segmented),
:deep(.el-form-item__label),
:deep(.el-tag) {
  font-family: var(--ui-serif);
}

:deep(.el-button) {
  font-weight: 600;
}

:deep(.el-button--primary) {
  --el-button-bg-color: var(--ui-accent);
  --el-button-border-color: var(--ui-accent);
  --el-button-text-color: #ffffff;
  --el-button-hover-bg-color: var(--ui-accent-light);
  --el-button-hover-border-color: var(--ui-accent-light);
  --el-button-active-bg-color: var(--ui-accent);
  --el-button-active-border-color: var(--ui-accent);
  --el-button-disabled-bg-color: #b8c4d5;
  --el-button-disabled-border-color: #b8c4d5;
  --el-button-disabled-text-color: #ffffff;
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner) {
  background: var(--ui-card);
  box-shadow: 0 0 0 1px var(--ui-border) inset;
}

:deep(.el-input__wrapper:hover),
:deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px var(--ui-soft) inset;
}

.workspace {
  height: 100vh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.workspace-head {
  position: sticky;
  top: 0;
  z-index: 8;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--ui-border);
  background: var(--ui-card);
}

.workspace-kicker {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0;
  text-transform: none;
  color: var(--ui-muted);
  font-weight: 600;
}

h2 {
  margin: 6px 0 0;
  font-size: 22px;
  line-height: 1.22;
  font-weight: 650;
  letter-spacing: 0;
}

.workspace-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ui-muted);
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.stream-detail {
  font-size: 12px;
  color: var(--ui-muted);
}

.model-service-tip {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--ui-muted);
}

.messages {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 14px 0;
}

.hydration-skeleton {
  max-width: 1040px;
  margin: 0 auto;
  padding: 0 20px;
  display: grid;
  gap: 10px;
}

.skeleton-line,
.skeleton-bubble {
  border-radius: 8px;
  background: linear-gradient(
    90deg,
    #e8e6dc,
    #eef2f7,
    #e8e6dc
  );
  background-size: 220% 100%;
  animation: shimmer 1.3s linear infinite;
}

.skeleton-line {
  height: 12px;
}

.skeleton-line.lg {
  width: 52%;
}

.skeleton-line.short {
  width: 36%;
}

.skeleton-bubble {
  height: 84px;
}

.skeleton-bubble.alt {
  width: 76%;
  justify-self: end;
}

.welcome-block {
  max-width: 840px;
  margin: 0 auto 18px;
  padding: 0 20px;
  text-align: center;
}

.welcome-block h3 {
  margin: 0;
  font-size: 34px;
  line-height: 1.2;
  font-weight: 750;
  color: var(--ui-text);
}

.welcome-block p {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--ui-muted);
}

.welcome-core {
  width: min(100%, 620px);
  min-height: 156px;
  margin: 22px auto 0;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 22px;
  background: color-mix(in srgb, var(--ui-card) 88%, transparent);
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 10px;
  box-shadow: 0 18px 40px rgba(27, 54, 93, 0.05);
}

.welcome-file-badge {
  width: 42px;
  height: 42px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: var(--ui-accent-tint);
  color: var(--ui-accent);
  font-size: 19px;
}

.welcome-upload-button {
  min-width: 184px;
  border: 1px solid var(--ui-accent);
  border-radius: 8px;
  padding: 12px 20px;
  background: var(--ui-accent);
  color: #ffffff;
  font-size: 14px;
  font-weight: 650;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  box-shadow: 0 14px 34px rgba(32, 61, 92, 0.14);
}

.welcome-upload-button:hover {
  background: var(--ui-accent-light);
  border-color: var(--ui-accent-light);
}

.welcome-upload-button:disabled {
  cursor: wait;
  opacity: 0.72;
}

.welcome-file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.welcome-core small,
.welcome-divider span {
  font-size: 12px;
  line-height: 1.5;
  color: var(--ui-muted);
}

.welcome-divider {
  width: min(100%, 440px);
  margin: 20px auto 0;
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--ui-muted);
}

.welcome-divider::before,
.welcome-divider::after {
  content: "";
  height: 1px;
  flex: 1;
  background: var(--ui-border);
}

.welcome-prompts {
  width: min(100%, 620px);
  margin: 20px auto 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.welcome-prompts button,
.message-actions button {
  border: 1px solid var(--ui-border);
  background: var(--ui-card);
  color: var(--ui-text);
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.welcome-prompts button {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--ui-card) 92%, transparent);
}

.virtual-spacer {
  width: 100%;
  pointer-events: none;
}

.message-row {
  max-width: 1040px;
  margin: 0 auto;
  padding: 0 20px 18px;
  display: block;
  align-items: flex-start;
}

.message-row.user {
  display: flex;
  justify-content: flex-end;
}

.bubble-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.message-row.user .bubble-wrap {
  width: min(100%, 820px);
  align-items: flex-end;
}

.bubble-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--ui-muted);
  font-size: 12px;
  font-weight: 500;
}

.status-dot {
  color: var(--ui-accent);
}

.bubble {
  width: min(100%, 960px);
  border-radius: 8px;
  border: 1px solid var(--ui-border);
  background: var(--ui-card);
  padding: 15px 17px;
}

.message-row.assistant .bubble {
  border: none;
  background: transparent;
  padding: 2px 0;
}

.message-row.user .bubble {
  background: var(--ui-accent-tint);
}

.assistant-skeleton {
  display: grid;
  gap: 8px;
}

.assistant-skeleton div {
  height: 12px;
  border-radius: 8px;
  background: linear-gradient(
    90deg,
    #e8e6dc,
    #eef2f7,
    #e8e6dc
  );
  background-size: 220% 100%;
  animation: shimmer 1.3s linear infinite;
}

.assistant-skeleton div:nth-child(3) {
  width: 65%;
}

.edit-box {
  display: grid;
  gap: 10px;
  width: min(76vw, 760px);
  max-width: 100%;
}

.edit-box :deep(.el-textarea__inner) {
  min-height: 144px !important;
  line-height: 1.65;
  padding: 10px 12px;
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.plain {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 15px;
}

.message-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.citation-panel {
  margin-top: 12px;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--ui-panel);
}

.citation-title {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--ui-muted);
}

.citation-card-list {
  display: grid;
  gap: 8px;
}

.citation-card {
  width: 100%;
  border: 1px solid var(--ui-border);
  background: var(--ui-card);
  color: var(--ui-text);
  border-radius: 8px;
  padding: 11px 12px;
  cursor: pointer;
  text-align: left;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
}

.citation-card:hover {
  border-color: var(--ui-accent);
}

.citation-card-number {
  color: var(--ui-accent);
  font-weight: 700;
  font-size: 13px;
}

.citation-card-main {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.citation-card-main strong {
  font-size: 14px;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.citation-card-main small {
  color: var(--ui-muted);
  font-size: 13px;
}

.citation-card-main span {
  color: var(--ui-muted);
  font-size: 13px;
  line-height: 1.6;
}

.citation-preview {
  display: grid;
  gap: 12px;
}

.citation-preview-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.citation-preview-meta strong {
  min-width: 0;
  font-size: 15px;
  line-height: 1.45;
}

.citation-preview-meta small {
  color: var(--ui-muted);
  font-size: 13px;
}

.citation-preview-snippet {
  margin: 0;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--ui-panel);
  color: var(--ui-text);
  font-size: 14px;
  line-height: 1.7;
}

.citation-preview-state {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 18px;
  background: var(--ui-panel);
  color: var(--ui-muted);
  font-size: 14px;
  text-align: center;
}

.citation-preview-state.error {
  color: #9f3a38;
}

.citation-preview-frame {
  width: 100%;
  height: min(68vh, 680px);
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  background: var(--ui-panel);
}

.evidence-panel ul {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 4px;
  color: var(--ui-muted);
  font-size: 12px;
}

.follow-up-suggestions {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--ui-border-soft);
  color: var(--ui-muted);
  font-size: 14px;
  line-height: 1.7;
}

.follow-up-suggestions p {
  margin: 0;
}

.follow-up-suggestions ol {
  margin: 6px 0;
  padding-left: 20px;
  display: grid;
  gap: 3px;
}

.follow-up-suggestions li {
  padding-left: 2px;
  color: var(--ui-text);
}

/* Processing timeline */
.trace-timeline-panel {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--ui-panel);
  border: 1px solid var(--ui-border);
}
.trace-timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  list-style: none;
}
.trace-timeline-panel[open] .trace-timeline-header {
  margin-bottom: 10px;
}
.trace-timeline-header::-webkit-details-marker {
  display: none;
}
.trace-timeline-header::marker {
  content: "";
}
.trace-timeline-title::before {
  content: "▸";
  display: inline-block;
  margin-right: 6px;
  color: var(--ui-muted);
}
.trace-timeline-panel[open] .trace-timeline-title::before {
  transform: rotate(90deg);
}
.trace-timeline-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--ui-muted);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.trace-timeline-meta {
  font-size: 11px;
  color: var(--ui-muted);
}
.trace-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.trace-timeline-step {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
  min-height: 48px;
}
.trace-timeline-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}
.trace-node {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--ui-accent);
  border: 2px solid var(--ui-card);
  box-shadow: 0 0 0 3px #d0dce9;
  flex-shrink: 0;
  z-index: 1;
}
.trace-action-tool_call .trace-node {
  background: var(--ui-accent-light);
  box-shadow: 0 0 0 3px #d0dce9;
}
.trace-action-finish .trace-node {
  background: var(--ui-muted);
  box-shadow: 0 0 0 3px #e8e6dc;
}
.trace-action-error .trace-node {
  background: #8b4513;
  box-shadow: 0 0 0 3px #f0e0d8;
}
.trace-connector {
  width: 2px;
  flex: 1;
  background: linear-gradient(180deg, #d0dce9, #e8e6dc);
  min-height: 20px;
}
.trace-timeline-content {
  padding-bottom: 14px;
}
.trace-last .trace-timeline-content {
  padding-bottom: 0;
}
.trace-step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.trace-step-label {
  font-weight: 500;
  font-size: 12px;
  color: var(--ui-text);
}
.trace-action-badge {
  padding: 1px 8px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--ui-card);
  background: var(--ui-accent);
}
.badge-thought {
  background: var(--ui-accent);
}
.badge-tool_call {
  background: var(--ui-accent-light);
}
.badge-finish {
  background: var(--ui-muted);
}
.badge-error {
  background: #8b4513;
}
.trace-field-label {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--ui-muted);
  margin-bottom: 3px;
  display: block;
}
.trace-thought-block p {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--ui-text);
}
.trace-input-block pre,
.trace-obs-block pre {
  margin: 4px 0 0;
  padding: 6px 8px;
  border-radius: 6px;
  background: var(--ui-card);
  font-size: 11px;
  overflow-x: auto;
  max-height: 100px;
  border: 1px solid var(--ui-border);
}
.trace-obs-block summary {
  cursor: pointer;
  padding: 2px 0;
}
.thinking {
  max-width: 1040px;
  margin: 0 auto;
  padding: 0 20px 10px 70px;
  color: var(--ui-muted);
  font-size: 13px;
}

.evaluation-page {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
}

.eval-side-panel,
.eval-main-panel {
  min-height: 0;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  background: var(--ui-card);
}

.eval-side-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.eval-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.eval-panel-head strong {
  display: block;
  margin-top: 4px;
  font-size: 22px;
}

.eval-dataset-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
}

.eval-dataset-list button {
  width: 100%;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 9px 10px;
  text-align: left;
  color: var(--ui-text);
  background: var(--ui-panel);
  cursor: pointer;
}

.eval-dataset-list button.active {
  border-color: var(--ui-accent);
  box-shadow: inset 0 0 0 1px #d0dce9;
}

.eval-dataset-list span,
.eval-dataset-list small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.eval-dataset-list span {
  font-size: 13px;
  font-weight: 700;
}

.eval-dataset-list small {
  margin-top: 4px;
  color: var(--ui-muted);
}

.eval-create-panel {
  display: grid;
  gap: 8px;
}

.eval-json-input :deep(.el-textarea__inner) {
  font-family: var(--ui-mono);
  font-size: 12px;
  line-height: 1.45;
}

.eval-main-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
}

.eval-score-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(112px, 1fr));
  gap: 8px;
}

.eval-metric-card {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 10px;
  background: var(--ui-panel);
  min-width: 0;
}

.eval-metric-card span,
.eval-metric-card small {
  display: block;
  font-size: 11px;
  color: var(--ui-muted);
}

.eval-metric-card strong {
  display: block;
  margin: 7px 0 4px;
  font-size: 22px;
  line-height: 1;
}

.eval-metric-card .good {
  color: var(--ui-accent);
}

.eval-metric-card .bad {
  color: #8b4513;
}

.eval-metric-card .neutral {
  color: var(--ui-muted);
}

.eval-run-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.eval-run-summary {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 10px;
  background: var(--ui-panel);
}

.eval-run-summary.current {
  border-color: var(--ui-accent);
}

.eval-run-summary strong,
.eval-run-summary span {
  display: block;
  margin-top: 6px;
}

.eval-run-summary strong {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.eval-run-summary span {
  font-size: 22px;
  font-weight: 800;
}

.eval-result-table {
  min-height: 0;
  border-radius: 10px;
  overflow: hidden;
}

.composer-shell {
  position: sticky;
  bottom: 0;
  z-index: 6;
  padding: 0 16px 16px;
  background: linear-gradient(
    180deg,
    transparent 0%,
    var(--ui-bg) 32%,
    var(--ui-bg) 100%
  );
}

.composer {
  max-width: 1040px;
  margin: 0 auto;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  background: var(--ui-card);
  padding: 10px;
}

.composer-footer {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 10px;
}

.composer-left {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.composer-controls {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.composer-quality-select {
  width: 124px;
}

.composer-quality-select :deep(.el-select__wrapper) {
  min-height: 30px;
  border-radius: 999px;
  background: var(--ui-panel);
  box-shadow: 0 0 0 1px var(--ui-border) inset;
}

.composer-toggle {
  height: 30px;
  border: 1px solid var(--ui-border);
  border-radius: 999px;
  padding: 0 10px;
  background: var(--ui-panel);
  color: var(--ui-muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.composer-toggle span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ui-soft);
}

.composer-toggle.active {
  color: var(--ui-accent);
  background: var(--ui-accent-tint);
  border-color: #d0dce9;
}

.composer-toggle.active span {
  background: var(--ui-accent);
}

.composer-actions {
  display: flex;
  gap: 8px;
}

.markdown :deep(p) {
  margin: 0 0 10px;
  font-size: 15px;
  line-height: 1.72;
}

.markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown :deep(ul),
.markdown :deep(ol) {
  margin: 0 0 10px;
  padding-left: 20px;
  font-size: 15px;
  line-height: 1.72;
}

.markdown :deep(code:not(.hljs)) {
  font-family: var(--ui-mono);
  background: var(--ui-accent-tint);
  border-radius: 4px;
  padding: 2px 5px;
  color: var(--ui-accent);
}

.markdown :deep(.code-block) {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--ui-card);
  color: var(--ui-text);
}

.markdown :deep(.code-toolbar) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: var(--ui-panel);
  border-bottom: 1px solid var(--ui-border);
}

.markdown :deep(.code-lang) {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--ui-muted);
}

.markdown :deep(.copy-code-btn) {
  border: 1px solid var(--ui-border);
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 11px;
  background: var(--ui-sand);
  color: var(--ui-text);
  cursor: pointer;
}

.markdown :deep(pre) {
  margin: 0;
  padding: 0;
  overflow-x: auto;
}

.markdown :deep(code.hljs) {
  display: block;
  padding: 10px 0;
  background: transparent;
  font-family: var(--ui-mono);
  font-size: 12px;
  line-height: 1.55;
}

.markdown :deep(.code-line) {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 10px;
  padding: 0 12px;
  white-space: pre;
}

.markdown :deep(.line-no) {
  user-select: none;
  color: var(--ui-soft);
  text-align: right;
}

.markdown :deep(.line-content) {
  min-width: 0;
}

.markdown :deep(.hljs-comment),
.markdown :deep(.hljs-quote) {
  color: var(--ui-soft);
}

.markdown :deep(.hljs-keyword),
.markdown :deep(.hljs-selector-tag),
.markdown :deep(.hljs-subst) {
  color: var(--ui-accent);
}

.markdown :deep(.hljs-string),
.markdown :deep(.hljs-doctag) {
  color: #504e49;
}

.markdown :deep(.hljs-title),
.markdown :deep(.hljs-section),
.markdown :deep(.hljs-selector-id) {
  color: var(--ui-accent-light);
}

.markdown :deep(.hljs-number),
.markdown :deep(.hljs-literal) {
  color: #8b4513;
}

:deep(.composer .el-textarea__inner) {
  border: none;
  box-shadow: none;
  background: transparent;
  font-size: 15px;
  line-height: 1.58;
  color: var(--ui-text);
}

:deep(.composer .el-textarea__inner:focus) {
  box-shadow: none;
}

@keyframes shimmer {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: -120% 0;
  }
}

@media (max-width: 1160px) {
  .app-shell {
    grid-template-columns: 300px minmax(0, 1fr);
  }

  .eval-score-strip {
    grid-template-columns: repeat(3, minmax(112px, 1fr));
  }
}

@media (max-width: 980px) {
  .app-shell {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 100vh;
    overflow: auto;
  }

  .sidebar {
    height: auto;
    overflow: visible;
    border-right: none;
    border-bottom: 1px solid var(--ui-border);
  }

  .workspace {
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .session-list,
  .branch-list {
    max-height: 170px;
  }

  .workspace-head {
    position: static;
  }

  .evaluation-page {
    grid-template-columns: 1fr;
  }

  .eval-dataset-list {
    max-height: 180px;
  }

  .composer-shell {
    position: static;
  }
}

@media (max-width: 680px) {
  .workspace-head {
    padding-left: 12px;
    padding-right: 12px;
  }

  .head-actions {
    justify-content: flex-start;
  }

  .evaluation-page {
    padding: 12px;
  }

  .eval-score-strip,
  .eval-run-grid {
    grid-template-columns: 1fr;
  }

  .welcome-block,
  .message-row,
  .thinking,
  .hydration-skeleton {
    padding-left: 12px;
    padding-right: 12px;
  }

  .message-row {
    display: block;
  }

  .welcome-prompts {
    grid-template-columns: 1fr;
  }

  .thinking {
    padding-left: 12px;
  }

  .composer-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
