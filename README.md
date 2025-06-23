# AI 视频字幕自动化翻译平台

这是一个基于 Spring Boot 构建的全自动视频翻译后端服务。本项目旨在为内容创作者提供一个高效、便捷的工具，只需上传原始视频，即可获得一份包含精准翻译字幕的成品视频。

## ✨ 主要功能

- **全自动处理流**：从视频上传、音频提取、语音转录到字幕翻译和视频压制，全程无需人工干预。
- **异步任务架构**：所有耗时操作（如转录、翻译）均在后台异步执行，用户可随时查询任务状态，不阻塞主线程。
- **高精度 AI 模型**：
  - **语音转录**：集成 [OpenAI Whisper](https://github.com/openai/whisper) 模型，保证了从音频到文字的高准确率。
  - **内容翻译**：对接大语言模型（LLM）API，实现流畅、自然且符合语境的字幕翻译。
- **硬件加速支持**：在压制字幕时，利用 FFmpeg 的 NVIDIA CUDA 硬件加速，大幅提升处理速度。
- **完整的用户与任务管理**：提供用户认证、任务管理、文件存储以及基于用量的计费功能。

## 🚀 工作流

1.  用户通过 API 上传视频文件。
2.  系统创建任务，并将其放入异步处理队列。
3.  **FFmpeg** 提取视频中的音轨，生成 MP3 文件。
4.  **Whisper** 模型将音频转录为原始语言的 SRT 字幕文件。
5.  **大语言模型 API** 接收 SRT 文件内容，进行分块、并行翻译。
6.  **FFmpeg** 将翻译好的 SRT 字幕“硬编码”到原视频中，生成最终成品。
7.  任务完成，用户可下载成品视频和字幕文件。

## 🛠️ 技术栈

- **后端**: Spring Boot, Spring Security
- **持久层**: MyBatis, MySQL
- **异步并发**: Spring `@Async`, `CompletableFuture`, `ExecutorService`
- **核心工具与服务**: 
  - FFmpeg (音视频处理)
  - NVIDIA CUDA (硬件加速)
  - OpenAI Whisper (语音转录)
  - 大语言模型 API (如豆包)
- **数据交互**: RESTful API, WebClient, RestTemplate, Jackson

## 快速开始

1.  **环境准备**:
    -   JDK 17+
    -   Maven / Gradle
    -   MySQL
    -   已安装并配置好环境变量的 FFmpeg
    -   已下载或安装好的 Whisper

2.  **克隆项目**:
    ```bash
    git clone https://github.com/your-username/your-repo-name.git
    ```

3.  **配置**:
    -   修改 `src/main/resources/application.properties` 文件。
    -   设置数据库连接信息 (`spring.datasource.*`)。
    -   配置大语言模型 API 的 `api-key` 和 `api-base`。
    -   指定 Whisper 可执行文件路径 (`whisper.executable-path`)。
    -   配置各文件存储目录 (`file.upload-dir`, `temp.audio-dir` 等)。

4.  **运行**:
    ```bash
    mvn spring-boot:run
    ```
