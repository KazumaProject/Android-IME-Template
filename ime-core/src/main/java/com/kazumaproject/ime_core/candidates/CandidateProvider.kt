package com.kazumaproject.ime_core.candidates

interface CandidateProvider {
    suspend fun suggest(bgText: String, limit: Int = 8): List<Candidate>
}

/**
 * 動作確認用のデフォルト実装（まずは “変換っぽい何か” を返すだけ）
 * - bgText そのまま
 * - ひらがな/カタカナ相当のダミー（必要ならあなたの実装に差し替え）
 */
class DefaultCandidateProvider : CandidateProvider {
    override suspend fun suggest(bgText: String, limit: Int): List<Candidate> {
        if (bgText.isBlank()) return emptyList()
        val base = bgText.trim()

        // 最小のダミー（本番は KanaKanjiConverter 等に差し替える）
        val list = listOf(
            Candidate(surface = base),
            Candidate(surface = "【$base】"),
            Candidate(surface = base.uppercase())
        )
        return list.take(limit)
    }
}
