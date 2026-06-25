# MoeMusic NekoCompat

使 NekoMusic Client 在连接到使用 MoeMusic 的服务端时能够正常工作。

## 安装

从 Releases 页面下载对应版本的 jar 文件，并将其放入服务端的 `mods` 目录中。

本插件仅做数据包协议转换，不提供命令兼容，不需要进行任何配置。服务端的播放配置、权限检查等均由 MoeMusic 本身处理。

## 版本和加载器

考虑原版 NekoMusic Server 仅支持 fabric，故我假设所有需要这个插件的服务端都使用 fabric，因此目前只适配了 fabric。

关于版本，目前仅适配 26.1 及以上版本的 Minecraft，需要 1.3.0 及以上版本的 MoeMusic。

如果你需要在其他版本或加载器上使用这个插件，可以提交 issue，或者也可以请 AI 帮忙适配一下。这个插件使用的平台接口非常少，AI 应该很容易搞定。

## 已知限制

- NekoMusic Client 只支持 mp3、flac 和 ogg 三种格式，而 MoeMusic 支持几乎所有常见格式，因此播放这些歌曲时，NekoMusic Client 会无法播放。
- NekoMusic Client 的数据包中，歌曲 ID 是长整数，而 MoeMusic 的歌曲 ID 是字符串，因此这个插件会将 MoeMusic 的歌曲 ID 通过哈希算法转换成长整数，理论上可能会存在哈希碰撞的情况，但实际使用中应该不会遇到。
- NekoMusic Client 不会发送客户端区域信息，因此服务端无法使用客户端的偏好语言来发送命令反馈。对简体中文用户来说，你可能需要调整 MoeMusic 服务端的默认语言为简体中文，来为玩家提供本地化的反馈。

## 许可证
AGPL-3.0-or-later
