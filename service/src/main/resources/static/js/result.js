import { createApp, ref, nextTick } from 'vue'

const createBasicSetup = function() {
  // --- CONFIG --------------------------------------------------------

  const config = {
    singleUrl: 'api/single/',
    logUrl: 'logs/',
    itemsUrl: 'api/items',
    timeUpdateRate: 1000,
    itemsUpdateRate: 5000,
  }

  // --- STATE ---------------------------------------------------------

  const loginList = ref([])

  // --- UPDATE TIME ---------------------------------------------------
  // periodically update text on elapsed time

  function getTimeDifference(timestamp) {
    const difference = Date.now() - timestamp
    const minutes = Math.floor(difference / (60*1000))
    const seconds = Math.floor((difference - minutes*60*1000) / 1000)

    var textMinutes
    if (minutes == 0) {
      textMinutes = ''
    } else if (minutes == 1) {
      textMinutes = minutes + ' minute'
    } else if (minutes > 0) {
      textMinutes = minutes + ' minutes'
    }
    var textSeconds = seconds + ' seconds'

    return textMinutes + ' ' + textSeconds
  }

  function updateTime() {
    loginList.value.forEach(item => {
        item.expiredTime = getTimeDifference(item.timestamp)
    })
  }
  updateTime() // initially
  const timeInterval = setInterval(updateTime, config.timeUpdateRate) // periodically

  // --- UPDATE ITEMS --------------------------------------------------
  // periodically load items from server

  function updateItems(data) {
    loginList.value = data.reduce((newList, newItem) => {
      const currentItem = loginList.value.find(x => x.timestamp == newItem.timestamp)
      if (currentItem) {
        return newList.concat([currentItem]) // re-use existing item
      } else {
        return newList.concat([newItem]) // add new item
      }
    }, [])
  }

  async function updateData() {
    try {
      console.log('updateData')

      let response = await fetch(config.itemsUrl)
      const data = await response.json()
      console.log('items: ', data)

      updateItems(data)
      updateTime()
    } catch (error) {
      console.log('error: ', error)
    }
  }

  async function startPeriodicUpdate() {
    updateData() // initially
    const itemInterval = setInterval(updateData, config.itemsUpdateRate) // periodically
  }

  async function updateItemById(id) {
    try {
      console.log('updateItemById')
      let response = await fetch(config.singleUrl + id)
      if (response.ok) {
        try {
          const item = await response.json()
          if (item != null) {
            console.log('authentication: ', item)
            updateItems([item])
          }
        } catch (error) {
          // do nothing
        }
      }
      let logResponse = await fetch(config.logUrl + id);
      if (logResponse.ok) {
        try {
          const data = await logResponse.json();
          data.forEach(it => console.log(it))
        } catch (error) {
          // do nothing
        }
      }
      updateTime()
    } catch (error) {
      console.log('error: ', error)
    }
  }

  // --- ACTIONS -------------------------------------------------------

  function toggleDetails(item) {
    // show details of item
    item.showDetails = !(item.showDetails == true)
  }

  // --- RETURNS -------------------------------------------------------

  return {
    loginList,
    toggleDetails,
    startPeriodicUpdate,
    updateItemById,
  }
}


export { createBasicSetup }
