<template>
  <div>
    <input type="file" @change="handleFileChange" />

    <button :disabled="!file || uploading" @click="uploadFile">
      {{ uploading ? "上传中..." : "上传" }}
    </button>

    <p v-if="message">{{ message }}</p>

    <p v-if="objectName">
      上传成功：{{ objectName }}
    </p>
  </div>
</template>

<script setup>
import { ref } from "vue";
import axios from "axios";

const file = ref(null);
const uploading = ref(false);
const message = ref("");
const objectName = ref("");

function handleFileChange(event) {
  file.value = event.target.files[0];
}

async function uploadFile() {
  if (!file.value) {
    message.value = "请选择文件";
    return;
  }

  const formData = new FormData();
  formData.append("file", file.value);

  uploading.value = true;
  message.value = "";

  try {
    const res = await axios.post(
      "http://localhost:8081/api/files/upload",
      formData,
      {
        headers: {
          "Content-Type": "multipart/form-data"
        }
      }
    );

    objectName.value = res.data.objectName;
    message.value = "上传成功";
  } catch (error) {
    console.error(error);
    message.value = "上传失败";
  } finally {
    uploading.value = false;
  }
}
</script>