// 对比后端城市列表与本地坐标字典的覆盖率
import http from 'http'

const options = {
  hostname: 'localhost',
  port: 5173,
  path: '/api/weather/cities?metric=rain',
  method: 'GET'
}

const req = http.request(options, (res) => {
  let data = ''
  res.on('data', (chunk) => { data += chunk })
  res.on('end', () => {
    try {
      const json = JSON.parse(data)
      const cities = json.data || []
      console.log('后端城市总数:', cities.length)

      import('../src/utils/cityCoords.js').then(({ cityCoords, getCityCoord }) => {
        const localKeys = Object.keys(cityCoords)
        console.log('本地坐标字典城市数:', localKeys.length)

        const matched = []
        const missing = []
        cities.forEach(city => {
          if (getCityCoord(city)) {
            matched.push(city)
          } else {
            missing.push(city)
          }
        })

        console.log('\n=== 覆盖率统计 ===')
        console.log('匹配成功:', matched.length)
        console.log('匹配失败:', missing.length)
        console.log('覆盖率:', ((matched.length / cities.length) * 100).toFixed(1) + '%')

        if (missing.length > 0) {
          console.log('\n=== 缺失的城市/地区 ===')
          missing.forEach((c, i) => console.log(`${i + 1}. ${c}`))
        }
      })
    } catch (e) {
      console.error('解析失败:', e.message)
    }
  })
})

req.on('error', (e) => {
  console.error('请求失败:', e.message)
})

req.end()
