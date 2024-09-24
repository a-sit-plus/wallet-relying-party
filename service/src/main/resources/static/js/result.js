import { createApp, ref, nextTick } from 'vue'

const createBasicSetup = function() {
  // --- CONFIG --------------------------------------------------------

  const config = {
    selfURL: 'api/self',
    itemsUrl: 'api/items',
    removeUrl: 'api/remove',
    timeUpdateRate: 1000,
    itemsUpdateRate: 5000,
  };

  // --- STATE ---------------------------------------------------------

  const loginList = ref([]);

  // --- UPDATE TIME ---------------------------------------------------
  // periodically update text on elapsed time

  function getTimeDifference(timestamp) {
    const difference = Date.now() - timestamp;
    const minutes = Math.floor(difference / (60*1000));
    const seconds = Math.floor((difference - minutes*60*1000) / 1000);

    var textMinutes;
    if (minutes == 0)
      textMinutes = '';
    else if (minutes == 1)
      textMinutes = minutes + ' minute';
    else if (minutes > 0)
      textMinutes = minutes + ' minutes';

    var textSeconds = seconds + ' seconds';

    return textMinutes + ' ' + textSeconds;
  }

  function updateTime() {
    loginList.value.forEach(item => {
        item.expiredTime = getTimeDifference(item.timestamp)
    })
  }
  updateTime(); // initially
  const timeInterval = setInterval(updateTime, config.timeUpdateRate); // periodically

  // --- UPDATE ITEMS --------------------------------------------------
  // periodically load items from server

  function updateItems(data) {
    loginList.value = data.reduce((newList, newItem) => {
      const currentItem = loginList.value.find(x => x.id == newItem.id);
      if (currentItem)
        return newList.concat([currentItem]); // re-use existing item
      else
        return newList.concat([newItem]); // add new item
    }, []);
  }

  async function updateData() {
    try {
      console.log('updateData');

      let response = await fetch(config.itemsUrl);
      const data = await response.json();
      console.log('items: ', data);

      updateItems(data);
      updateTime();
    } catch (error) {
      console.log('error: ', error);
    }
  }

  async function startPeriodicUpdate() {
    updateData(); // initially
    const itemInterval = setInterval(updateData, config.itemsUpdateRate); // periodically
  }

  async function updateByCookie() {
    try {
      console.log('updateByCookie');

      let response = await fetch(config.selfURL, {
          credentials: 'include'
      });
      const item = await response.json();
      console.log('item: ', item);

      updateItems([item]);
      updateTime();
    } catch (error) {
      console.log('error: ', error);
    }
  }

  // --- ACTIONS -------------------------------------------------------

  async function seen(item) {
    try {
      console.log('remove: ', item.id);

      // notify server that record can be removed
      const response = await fetch(config.removeUrl, {
        method: 'POST',
        body: item.id
      });
      const data = await response.json();
      console.log('response: ', data);

      // on OK, remove item from list
      if (response.ok)
        loginList.value.splice(loginList.value.indexOf(item), 1)
    } catch (error) {
      console.log('error: ', error);
    }
  }

  function toggleDetails(item) {
    // show details of item
    item.showDetails = !(item.showDetails == true)
  }

  function process(item) {
    alert("Full data: \n" + JSON.stringify(item["allFields"], null, 2));
  }

  // --- FORMATTERS ----------------------------------------------------

  function parseAddressBase64(input) {
    try {
      return JSON.parse(atob(input));
    } catch {
      try {
        return JSON.parse(input);
      } catch {
        return {}
      }
    }
  }

  function parseAddress(input) {
    try {
      var address = parseAddressBase64(input);
      if (address["Strasse"] && address["Strasse"] !== "") {
        var result = address["Strasse"] + " " + address["Hausnummer"];
        if (address["Stiege"] && address["Stiege"].trim())
          result += "/" + address["Stiege"];
        if (address["Tuer"] && address["Tuer"].trim())
          result += "/" + address["Tuer"];
        result += ", " + address["Postleitzahl"] + " " + address["Ortschaft"];
        return result;
      } else if (address["thoroughfare"] && address["thoroughfare"] !== "") {
        var result = address["thoroughfare"] + " " + address["locator_designator"];
        result += ", " + address["post_code"] + " " + address["post_name"];
        return result;
      } else {
        return input;
      }
    } catch {
      return input;
    }
  }

  function filterClaims(item) {
    const deepCopy = JSON.parse(JSON.stringify(item));
    delete deepCopy.timestamp;
    delete deepCopy.allFields;
    delete deepCopy.expiredTime;
    delete deepCopy.imageDataBase64;
    delete deepCopy.showDetails;
    return deepCopy;
  }

  // --- CHECKER -------------------------------------------------------

  function isSet(value) {
    return value && value != "N/A" && value != "data:image;base64,null" ;
  }

  // --- RETURNS -------------------------------------------------------

  return {
    loginList,
    seen,
    toggleDetails,
    process,
    parseAddress,
    isSet,
    filterClaims,
    startPeriodicUpdate,
    updateByCookie,
  }
}


export { createBasicSetup }
