package com.example.panoviewer.link;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.panoviewer.net.HttpHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 夸克网盘分享解析器（best-effort，独立可替换类）。
 *
 * <p>已知分享流程（接口可能已变更，重点在于结构正确 + 失败时抛 {@link ResolveException}）：</p>
 * <ol>
 *   <li>从分享链接提取 {@code pwd_id}；</li>
 *   <li>POST 分享 token 接口，用 {@code pwd_id + passcode} 换取 {@code stoken}；</li>
 *   <li>POST 分享 detail 接口，用 {@code pwd_id + stoken} 取文件列表；</li>
 *   <li>对图片类文件取 {@code download_url}（缺失则回退为分享页链接），封装为
 *       {@link ResolvedItem}。</li>
 * </ol>
 *
 * <p>依赖注入 {@link HttpHelper}。</p>
 */
public class QuarkShareResolver implements LinkResolver {

    private static final String SHARE_TOKEN_API =
            "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc";
    private static final String SHARE_DETAIL_API =
            "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail?pr=ucpro&fr=pc";

    private static final Pattern PWD_ID_PATH = Pattern.compile("pan\\.quark\\.cn/s/([A-Za-z0-9]+)");
    private static final Pattern PWD_ID_QUERY = Pattern.compile("[?&]pwd_id=([^&]+)");

    @NonNull
    private final HttpHelper httpHelper;

    public QuarkShareResolver(@NonNull HttpHelper httpHelper) {
        this.httpHelper = httpHelper;
    }

    @NonNull
    @Override
    public LinkType detect(@NonNull String url) {
        return LinkType.QUARK_SHARE;
    }

    @NonNull
    @Override
    public List<ResolvedItem> resolve(@NonNull String url, @Nullable String passcode) {
        try {
            String pwdId = extractPwdId(url);
            if (pwdId == null || pwdId.isEmpty()) {
                throw new ResolveException("无法从夸克分享链接提取 pwd_id");
            }

            // 1) 换取 stoken
            JSONObject tokenReq = new JSONObject();
            tokenReq.put("pwd_id", pwdId);
            tokenReq.put("passcode", passcode == null ? "" : passcode);
            String tokenResp = httpHelper.postJson(SHARE_TOKEN_API, tokenReq.toString());
            String stoken = readPath(tokenResp, "data", "stoken");
            if (stoken == null || stoken.isEmpty()) {
                throw new ResolveException("夸克未返回 stoken（可能需要提取码或链接已失效）");
            }

            // 2) 取分享详情（文件列表）
            JSONObject detailReq = new JSONObject();
            detailReq.put("pwd_id", pwdId);
            detailReq.put("stoken", stoken);
            detailReq.put("pdir_fid", "0");
            detailReq.put("force", 0);
            String detailResp = httpHelper.postJson(SHARE_DETAIL_API, detailReq.toString());
            JSONArray fileList = readArray(detailResp, "data", "list");
            if (fileList == null) {
                throw new ResolveException("夸克分享详情缺少文件列表");
            }

            // 3) 收集图片类文件
            List<ResolvedItem> items = new ArrayList<>();
            for (int i = 0; i < fileList.length(); i++) {
                JSONObject f = fileList.getJSONObject(i);
                String fileName = f.optString("file_name", "");
                if (!LinkUtils.isImageUrl(fileName)) {
                    continue;
                }
                String fid = f.optString("fid", "");
                String durl = f.optString("download_url", "");
                String title = fileName.isEmpty() ? ("quark_" + fid) : fileName;
                // 能拿到直链就用直链；否则回退为分享页链接（用户可在浏览器打开）
                String resolvedUrl = (durl == null || durl.isEmpty()) ? url : durl;
                items.add(new ResolvedItem(resolvedUrl, title, LinkType.QUARK_SHARE,
                        f.optLong("size", 0), null));
            }

            if (items.isEmpty()) {
                // 回退：没有可解析图片时，至少把分享页返回，引导用户浏览器打开
                items.add(new ResolvedItem(url, "夸克分享（请浏览器打开）", LinkType.QUARK_SHARE, 0, null));
            }
            return items;
        } catch (ResolveException re) {
            throw re;
        } catch (Exception e) {
            // 接口变更 / 网络异常等，统一转为解析失败异常，由上层转 Toast
            throw new ResolveException("夸克解析失败：" + e.getMessage(), e);
        }
    }

    @Nullable
    private static String extractPwdId(@NonNull String url) {
        Matcher m = PWD_ID_PATH.matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        m = PWD_ID_QUERY.matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /** 从 JSON 字符串中按 对象路径 读取字符串字段。 */
    @Nullable
    private static String readPath(@NonNull String json, String... keys) {
        try {
            JSONObject obj = new JSONObject(json);
            for (int i = 0; i < keys.length - 1; i++) {
                obj = obj.getJSONObject(keys[i]);
            }
            return obj.optString(keys[keys.length - 1], "");
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 JSON 字符串中按 对象路径 读取数组字段。 */
    @Nullable
    private static JSONArray readArray(@NonNull String json, String... keys) {
        try {
            JSONObject obj = new JSONObject(json);
            for (int i = 0; i < keys.length - 1; i++) {
                obj = obj.getJSONObject(keys[i]);
            }
            return obj.optJSONArray(keys[keys.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }
}
