import { listNotifications } from '@/api/lab/notification'

const POLL_INTERVAL = 60000

const useLabNotificationStore = defineStore('lab-notification', {
  state: () => ({
    unreadCount: 0,
    loading: false,
    timer: undefined
  }),
  actions: {
    async refreshUnreadCount() {
      if (this.loading) return this.unreadCount
      this.loading = true
      try {
        const response = await listNotifications({
          unreadOnly: true,
          pageNum: 1,
          pageSize: 1
        })
        this.unreadCount = Number(response?.total ?? 0)
        return this.unreadCount
      } finally {
        this.loading = false
      }
    },
    startPolling() {
      if (this.timer) return
      void this.refreshUnreadCount().catch(() => undefined)
      this.timer = globalThis.setInterval(() => {
        void this.refreshUnreadCount().catch(() => undefined)
      }, POLL_INTERVAL)
    },
    stopPolling() {
      if (!this.timer) return
      globalThis.clearInterval(this.timer)
      this.timer = undefined
    },
    consumeOne() {
      this.unreadCount = Math.max(0, this.unreadCount - 1)
    }
  }
})

export default useLabNotificationStore
