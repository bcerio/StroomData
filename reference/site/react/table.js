var TableView = React.createClass({
	onClick:function(e){
		var target=e.target
		while(target.id==''){
			target=target.parentNode
		}
		if("selectRow" in this.props){
			this.props.selectRow(target.id)
		}
	},
	renderHeader:function(){
		var fields=[]
		for(var i=0;i<this.props.columns.length;i++){
			var column=this.props.columns[i]
			var width=column.width
			var title=column.key
			fields.push(h('div.data_cell.'+width,title))
		}
		return h('div.data_header',fields)
	},
	renderRows:function(){
		var rows=[]
		for(var n=0;n<this.props.data.length;n++){
			var data=this.props.data[n]
			var fields=[]
			var id=this.props.id+'-'+n
			if("id" in data){
				id=this.props.id+'-'+data.id
			}
			if(this.props.rowClass!=null){
				fields=h(this.props.rowClass,data)
			}else{
				for(var i=0;i<this.props.columns.length;i++){
					var column=this.props.columns[i]
					var width=column.width
					var key=column.key
					fields.push(h('div.data_cell.'+width,data[key]))
				}
			}
			var classname='div.data_row'
			classname+='#'+id
			if(this.props.selected==id && this.props.inspectorClass!=null){
				classname='div.data_row.open_row#'+id
				rows.push(h(classname,{'onClick':this.onClick},fields))
				rows.push(h(this.props.inspectorClass,data))
			}else{
				rows.push(h(classname,{'onClick':this.onClick},fields))
			}
		}
		return h('div.data_table',rows)
	},
	render:function(){
		var elements=[]

		elements.push(this.renderHeader())
		elements.push(this.renderRows())
		return h('div',elements)
	}
})
