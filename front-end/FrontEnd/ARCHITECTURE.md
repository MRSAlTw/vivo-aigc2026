# 项目架构说明（DI / core / feature）

本文件把之前讨论的内容整理为可读的文档，方便团队查阅：什么是 DI、Hilt 在项目中的角色；`core` 目录下各类文件职责；各 `feature` 模块下常见目录与文件的作用；以及常见问题（例如 MissingBinding）与处理建议。

## 小计划（Checklist）
- 解释依赖注入（DI）和 Hilt 的常用注解与作用
- 说明 `core` 包下关键文件/接口职责（以项目当前文件为例）
- 说明 `feature` 下常见子目录（domain/state/viewmodel/ui/di）的作用，并用实际文件举例
- 解释之前出现的 `MissingBinding` 的原因与长期解决方案
- 给出下一步建议（如何从 mock 切换到真实实现、测试策略）

---

## 1) 什么是 DI（依赖注入）？在本项目中意味着什么
- 概念：依赖注入（Dependency Injection，DI）将组件所需的依赖由容器提供，而不是组件自己 new 出来。好处包括解耦、便于测试、统一管理生命周期。
- 工具：本项目使用 Hilt（基于 Dagger 的 Android 友好封装）。
- Hilt 常见注解与含义：
  - `@Module` / `@InstallIn(...)`：声明一个模块并指定绑定作用域（例如 `SingletonComponent`, `ViewModelComponent`）。
  - `@Provides`：在模块中提供具体依赖的构造方法（返回具体实现）。
  - `@Binds`：绑定接口到实现（通常在实现类使用 `@Inject constructor()` 时用 `@Binds`）。
  - `@Inject`：标注构造函数或字段，表示 Hilt 可注入该依赖。
  - `@HiltViewModel`：标注 ViewModel，Hilt 会把它放入 ViewModel map，并允许 `hiltViewModel()` 获取。
- 运行机制：Hilt 在编译阶段生成 component / factory 代码。如果接口没有任何 binding（没有 `@Provides` / `@Binds`），编译器会报 `MissingBinding` 错误。

---

## 2) `core` 目录（共享契约与工具）
`core` 放置共享契约（接口）、数据模型、公共 UI 组件与工具代码。下面是项目中已有文件与职责说明：

- `core/camera/CameraFrame.kt`
  - 封装单帧相机数据（Bitmap、像素、元数据等），供上层 Overlay 与 domain 层分析使用。

- `core/camera/CameraController.kt`
  - 抽象相机控制（打开/关闭、切换镜头、拍照、输出流等），上层通过接口调用具体实现，便于替换与测试。

- `core/ai/AiEngine.kt`（或相关 AI 类型）
  - 封装/抽象 AI 能力（目标检测、姿态估计、场景分析），domain 层通过该契约调用 AI 服务。

- `core/data/model/PoseTemplate.kt`（以及 `PoseCategory`）
  - 姿势模板数据模型（id、name、category、thumbnailPath、keypoints 等）。

- `core/data/repository/PoseTemplateRepository.kt`
  - 姿势模板的 repository 接口（CRUD 与查询方法），ViewModel 仅依赖此接口。

- `core/data/repository/SceneExplorationRepository.kt`
  - 寻景模块的 repository/usecase 接口（例如 `observeBestAngle`、`startExplore`、`stopExplore`）。

- `core/ui/components/GuidanceOverlay.kt`
  - Compose 可复用的 Overlay 组件（九宫格、引导箭头等），纯渲染逻辑，无业务实现。

- `core/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`
  - 应用主题与颜色常量、Typography 定义，负责全局 UI 风格。

- `core/voice/VoiceController.kt`
  - 语音交互控制器接口（ASR/TTS），为 voice feature 提供抽象契约。

- `core/common/*`（如 Logger、AppResult 等）
  - 通用工具或结果封装，供各模块复用。

> 总结：`core` 只包含契约、模型与通用 UI/工具，不包含 feature 具体实现。

---

## 3) `feature` 目录下的常见分层与作用（以 `pose`, `exploration`, `voice`, `composition` 为例）
每个 feature 通常包含以下子目录：

- `domain/`
  - 业务逻辑与 UseCase 接口（与 Android UI 解耦）。例如：
    - `feature/exploration/domain/SceneExplorer.kt`：定义 `observeAngleAdvice(frames: Flow<CameraFrame>): Flow<AngleAdvice>`。
    - `feature/composition/domain/CompositionRuleEngine.kt`：定义 `evaluate(objects, mode): CompositionResult`。

- `state/`（UI 状态与一次性事件）
  - `*UiState`：StateFlow 持有的状态数据（例如 `isExploring`, `isCapturing`, `guidanceAngle` 等）。
  - `*UiEvent`：一次性事件（Toast、导航、拍照完成），通常通过 Channel 发送。

- `viewmodel/`（状态管理）
  - 持有 `MutableStateFlow` / `StateFlow`，在 `viewModelScope` 中调用 domain 或 repository，转换为 UI State。
  - 注入依赖（Hilt）：`@HiltViewModel` + `@Inject constructor(...)`。
  - 例：`ExplorationViewModel` 提供 `onStartExplore()`、`onStopExplore()`、`onShutterClick()`。

- `ui/`（Compose 屏幕）
  - Composable Screen/Route，使用 `hiltViewModel()` 获取 ViewModel，将 `state` 与回调绑定到 UI 控件。
  - 例：`ExplorationScreen.kt` 使用 `GuidanceOverlay`、按钮绑定 `viewModel::onStartExplore`。

- `di/`（Hilt 绑定模块）
  - 提供本 feature 的实现绑定（如把 `PoseTemplateRepository` 绑定到具体实现）。
  - 可以使用 `@Provides` 或 `@Binds`。若实现类使用 `@Inject constructor()`，优先使用 `@Binds`。

> 示例：MissingBinding 是因为 `PoseTemplateRepository` 作为接口被 `PoseViewModel` 依赖，但没有在 `feature/pose/di` 或 AppModule 提供对应的绑定，实现才会造成编译失败。

---

## 4) 为什么会出现 `MissingBinding`？如何长期正确处理
- 报错含义：Hilt 在生成组件时找不到可用于提供某个类型（接口）的绑定/提供者。
- 典型场景：ViewModel 构造函数依赖了接口类型，但项目中没有 `@Module` 提供实现，也没有实现类使用 `@Inject constructor()` 并被 `@Binds` 绑定。
- 解决方法（任选其一）：
  1. 在 `feature/.../di` 中写一个 `@Module`，使用 `@Provides` 返回实现（适用于需要运行时参数或你想手动构造实现的情况）；
  2. 给实现类添加 `@Inject constructor(...)` 并在模块中用 `@Binds abstract fun bindRepo(impl: Impl): PoseTemplateRepository`（更简洁、推荐当实现类可以被 Hilt 直接构造时）。
- 我在临时修复时采用了 `@Provides` + 简单 mock 实现，让项目可以先编译通过；长期建议将真实实现改为 `@Inject constructor` 并使用 `@Binds`。

---

## 5) 下一步建议（替换 mock -> 真实实现 与 测试）
- 开发流程建议：
  1. 先使用 `feature/*/di` 中的 mock 实现快速迭代 UI 与 navigation；
  2. 在实现真实功能时，把实现类标注 `@Inject constructor(...)`，并在同模块的 `di` 使用 `@Binds` 绑定接口到实现；
  3. 对于需要 Context/DAO/Retrofit 的实现，使用 `@Provides` 注入所需参数（或将依赖项也标注 `@Inject`）；
  4. 编写 ViewModel 的单元测试，使用替换实现（或 Mockito）来验证 state 转换与事件发送。

- Scope 提示：
  - repository、控制器、SDK 封装：通常使用 `SingletonComponent`（全局单例）；
  - 若仅在 ViewModel 级别存在：使用 `ViewModelComponent` 或直接注入到 ViewModel（@HiltViewModel + 构造注入）。

- 示例 Gradle 构建（快速校验）：
```powershell
# 在项目 FrontEnd 根目录下运行（Windows PowerShell）
.\gradlew clean build
```

---

如果你想，我可以：
- 把当前的 `mock -> binds` 模式转换为 `@Binds` + `@Inject`（更推荐），并为每个 feature 生成模板 module；或者
- 为每个 feature 生成一页 README（职责/后续 TODO/接口），或者
- 画一张简单的依赖图（哪些模块依赖 core / 哪些实现位于 feature）。

告诉我你更希望我接下来做哪一项，我就继续执行。
