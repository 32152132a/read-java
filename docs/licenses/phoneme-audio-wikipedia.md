# 音标音频署名与许可

当前首批音标音频来自 [joshstephenson/PhoneticFlashCards](https://github.com/joshstephenson/PhoneticFlashCards/tree/0fb0513d311175564f71bd7fcdca41d54f38f297/ipa_audio)。该仓库说明原始录音来自 Wikipedia 的 IPA 元音与辅音音频表，并以 [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) 提供。

应用使用仓库已经转换的 MP3 文件，没有剪辑或拼接录音；上传到 COS 时仅调整对象文件名。逐个音标、原始文件名、源符号、对象路径和特殊映射说明记录在 [phoneme-audio-wikipedia.json](phoneme-audio-wikipedia.json)。

首批仅使用与英语教学音标对应关系可靠的文件。长元音、双元音、`/tr/` 和 `/dr/` 暂不套用基础 IPA 音素录音，继续使用页面已有的例词朗读回退。

分发这些音频时必须保留来源、许可证链接和修改说明。如果未来对音频进行剪辑、拼接或其他改编，改编后的音频继续按 CC BY-SA 3.0 或兼容许可证分发。
