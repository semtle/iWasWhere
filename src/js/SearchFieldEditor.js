import React, {Component} from 'react';
import Editor, {createEditorStateWithText} from 'draft-js-plugins-editor'; // eslint-disable-line import/no-unresolved
import createMentionPlugin, {defaultSuggestionsFilter} from 'draft-js-mention-plugin'; // eslint-disable-line import/no-unresolved
import {fromJS} from 'immutable';
import editorStyles from './editorStyles.css';

const suggestionsFilter = (searchValue, suggestions) => {
    const value = searchValue.toLowerCase();
    const filteredSuggestions = suggestions.filter((suggestion) => {
        const name = suggestion.get("name").toLowerCase();
        const match = name.indexOf(value);
        return match > -1;
    });
    const size = filteredSuggestions.size < 15 ? filteredSuggestions.size : 15;
    return filteredSuggestions.setSize(size);
};

export default class SearchFieldEditor extends Component {

    state = {};

    onSearchChange = ({value}) => {
        let mentions = fromJS(this.props.mentions);
        this.setState({
            mentionSuggestions: defaultSuggestionsFilter(value, mentions),
        });
    };

    onSearchChange2 = ({value}) => {
        let hashtags = fromJS(this.props.hashtags);
        this.setState({
            hashtagSuggestions: defaultSuggestionsFilter(value, hashtags),
        });
    };

    onSearchChangeStories = ({value}) => {
        let stories = fromJS(this.props.stories);
        this.setState({
            storySuggestions: suggestionsFilter(value, stories),
        });
    };

    focus = () => {
        this.editor.focus();
    };

    onAddMention = () => {
        // get the mention object selected
    };

    onAddStory = (story) => {
    };

    constructor(props) {
        super(props);
        console.log(props);

        this.state.editorState = props.editorState;

        const hashtagPlugin = createMentionPlugin({
            mentionTrigger: "#",
        });

        const mentionPlugin = createMentionPlugin({
            mentionTrigger: "@",
        });

        const storyPlugin = createMentionPlugin({
            mentionTrigger: "$",
        });

        this.plugins = [hashtagPlugin, mentionPlugin, storyPlugin];
        this.HashtagSuggestions = hashtagPlugin.MentionSuggestions;
        this.MentionSuggestions = mentionPlugin.MentionSuggestions;
        this.StorySuggestions = storyPlugin.MentionSuggestions;

        this.state.mentionSuggestions = fromJS(props.mentions);
        this.state.hashtagSuggestions = fromJS(props.hashtags);
        this.state.storySuggestions = fromJS(props.stories);

        this.onChange = (editorState) => {
            props.onChange(editorState);
            this.setState({editorState});
        };
    }

    render() {
        const HashtagSuggestions = this.HashtagSuggestions;
        const MentionSuggestions = this.MentionSuggestions;
        const StorySuggestions = this.StorySuggestions;
        return (
            <div className="search-field"
                 onClick={this.focus}>
                <Editor
                    editorState={this.state.editorState}
                    onChange={this.onChange}
                    plugins={this.plugins}
                    ref={(element) => {
                        this.editor = element;
                    }}
                />
                <MentionSuggestions
                    onSearchChange={this.onSearchChange}
                    suggestions={this.state.mentionSuggestions}
                    onAddMention={this.onAddMention}
                />
                <HashtagSuggestions
                    onSearchChange={this.onSearchChange2}
                    suggestions={this.state.hashtagSuggestions}
                    onAddMention={this.onAddMention}
                />
                <StorySuggestions
                    onSearchChange={this.onSearchChangeStories}
                    suggestions={this.state.storySuggestions}
                    onAddMention={this.onAddStory}
                />
            </div>
        );
    }
}
