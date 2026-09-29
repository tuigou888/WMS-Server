// 统一拉起微信支付工具
// 流程（对齐官方文档 4012791898 小程序调起支付）：
// 1. 调后端 prepay 拿调起支付参数
// 2. mock 模式（params.mock===true）：直接调 mockPay 落单，跳过 uni.requestPayment
// 3. 真实模式：调 uni.requestPayment({timeStamp,nonceStr,package,signType:'RSA',paySign})
//    - success：后端回调已落单，前端仅刷新展示
//    - fail：区分「用户取消」与其他失败
import { orders as orderApi } from '@/api/market.js'

// 真实模式下 requestPayment success ≠ 后端回调已落单：以轮询查单为准（官方要求），最多确认 attempts 次
function pollPaid(orderId, attempts = 8, intervalMs = 1500) {
  return new Promise((resolve) => {
    let n = 0
    const tick = () => {
      n += 1
      orderApi.detail(orderId).then((o) => {
        if (o && ['PAID', 'REFUNDING', 'REFUNDED'].includes(o.payStatus)) return resolve(true)
        if (n >= attempts) return resolve(false)
        setTimeout(tick, intervalMs)
      }).catch(() => {
        if (n >= attempts) return resolve(false)
        setTimeout(tick, intervalMs)
      })
    }
    tick()
  })
}

/**
 * 发起支付。返回 Promise，resolve 表示支付流程完成，reject 表示失败或取消。
 * resolve 值：{ mock, confirmed }——confirmed=true 表示后端已确认到账；false 表示收银台已成功但落单待确认。
 * @param {number|string} orderId 订单 ID
 */
export function requestPayment(orderId) {
  return orderApi.prepay(orderId).then((params) => {
    if (!params) throw new Error('未获取到支付参数')
    // mock 模式：直接确认支付落单
    if (params.mock === true) {
      return orderApi.mockPay(orderId).then(() => ({ mock: true, confirmed: true }))
    }
    // 真实模式：拉起微信收银台
    return new Promise((resolve, reject) => {
      uni.requestPayment({
        provider: 'wxpay',
        timeStamp: params.timeStamp,
        nonceStr: params.nonceStr,
        package: params.package,
        signType: params.signType || 'RSA',
        paySign: params.paySign,
        success: () => pollPaid(orderId).then((confirmed) => resolve({ mock: false, confirmed })),
        fail: (err) => {
          const msg = (err && err.errMsg) || ''
          // 用户取消支付
          if (msg.indexOf('cancel') >= 0) {
            reject(new PaymentCancelled('用户取消支付'))
          } else {
            reject(new Error(msg || '调起支付失败'))
          }
        },
      })
    })
  })
}

// 用户取消支付的专用错误类型，便于调用方区分提示
export class PaymentCancelled extends Error {
  constructor(message) {
    super(message)
    this.name = 'PaymentCancelled'
    this.cancelled = true
  }
}
