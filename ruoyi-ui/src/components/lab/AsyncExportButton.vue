<template><el-button v-if="allowed" plain icon="Download" :loading="loading" @click="submit">异步导出</el-button></template>
<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import useUserStore from '@/store/modules/user'
import { exportTask } from '@/api/lab/businessTasks'
const props=defineProps({kind:{type:String,required:true},filters:{type:Object,default:()=>({})}})
const user=useUserStore(),router=useRouter(),loading=ref(false)
const allowed=computed(()=>user.permissions.includes('*:*:*')||user.permissions.includes('lab:task:export'))
const keys={LABORATORY:['keyword','status'],DEVICE:['keyword','status','laboratoryId','categoryCode'],RESERVATION:['status','reservationNo','deviceId','applicantId','from','to'],REPAIR:['status','repairNo','deviceId'],HAZARD:['status','severity','ownerId']}
async function submit(){loading.value=true;try{
  const filters=Object.fromEntries((keys[props.kind]||[]).filter(k=>props.filters[k]!==undefined&&props.filters[k]!==null&&props.filters[k]!=='').map(k=>[k,String(props.filters[k])]))
  await exportTask(props.kind,filters);ElMessage.success('任务已提交，请在任务中心查看进度');await router.push('/lab/task-center')
}catch(e){ElMessage.error(e.message||'导出提交失败')}finally{loading.value=false}}
</script>
